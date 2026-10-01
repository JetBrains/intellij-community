// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.io.DataInputOutputUtil
import it.unimi.dsi.fastutil.ints.IntArrayList
import it.unimi.dsi.fastutil.ints.IntList
import it.unimi.dsi.fastutil.ints.IntLists
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongMaps
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

@Internal
class TimestampsImmutable {

  private interface InputAdapter {
    @Throws(IOException::class)
    fun readTime(): Long

    @Throws(IOException::class)
    fun hasRemaining(): Boolean

    @Throws(IOException::class)
    fun readInt(): Int
  }

  companion object {
    //FIXME RC: this call to application makes us use ApplicationRule in tests for TimestampsImmutable -- which
    //          introduce completely superficial coupling, because TimestampsImmutable logic has nothing to do with application
    private val IS_UNIT_TEST = ApplicationManager.getApplication().isUnitTestMode()

    @JvmField
    val EMPTY: TimestampsImmutable = TimestampsImmutable(0, IntLists.emptyList(), IntLists.emptyList())

    @JvmStatic
    @Throws(IOException::class)
    fun readTimestamps(stream: DataInputStream?): TimestampsImmutable {
      if (stream != null) {
        return readTimestamps(object : InputAdapter {
          @Throws(IOException::class)
          override fun readTime(): Long {
            return DataInputOutputUtil.readTIME(stream)
          }

          @Throws(IOException::class)
          override fun hasRemaining(): Boolean {
            return stream.available() > 0
          }

          @Throws(IOException::class)
          override fun readInt(): Int {
            return DataInputOutputUtil.readINT(stream)
          }
        })
      }
      else {
        return TimestampsImmutable(Object2LongMaps.emptyMap())
      }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readTimestamps(buffer: ByteBuffer?): TimestampsImmutable {
      if (buffer != null) {
        buffer.order(ByteOrder.BIG_ENDIAN) //to be compatible with .writeToStream()
        return readTimestamps(object : InputAdapter {
          override fun readTime(): Long {
            return DataInputOutputUtil.readTIME(buffer)
          }

          override fun hasRemaining(): Boolean {
            return buffer.hasRemaining()
          }

          override fun readInt(): Int {
            return DataInputOutputUtil.readINT(buffer)
          }
        })
      }
      else {
        return TimestampsImmutable(Object2LongMaps.emptyMap())
      }
    }


    @Throws(IOException::class)
    private fun readTimestamps(stream: InputAdapter): TimestampsImmutable {
      val outdatedIndices = IntArrayList()
      //'header' is either timestamp (dominatingIndexStamp), or, if timestamp is small enough
      // (<MAX_SHORT), it is really a number of 'outdatedIndices', followed by actual indices
      // ints (which is index id from ID class), and followed by another timestamp=dominatingIndexStamp
      // value
      var dominatingIndexStamp = stream.readTime()
      val diff = dominatingIndexStamp - DataInputOutputUtil.timeBase
      if (diff > 0 && diff < ID.MAX_NUMBER_OF_INDICES) {
        val numberOfOutdatedIndices = diff.toInt()
        outdatedIndices.ensureCapacity(numberOfOutdatedIndices)
        repeat(numberOfOutdatedIndices) {
          outdatedIndices.add(stream.readInt())
        }
        dominatingIndexStamp = stream.readTime()
      }

      //and after is just a set of ints -- Index IDs from ID class
      val upToDateIndexIds = IntArrayList()
      while (stream.hasRemaining()) {
        upToDateIndexIds.add(stream.readInt())
      }

      if (upToDateIndexIds.isEmpty() && outdatedIndices.isEmpty()) {
        assert(dominatingIndexStamp == DataInputOutputUtil.timeBase) {
          "dominatingIndexStamp=$dominatingIndexStamp != timeBase=${DataInputOutputUtil.timeBase}"
        }
        dominatingIndexStamp = 0 //MAYBE RC: return EMPTY?
      }
      return TimestampsImmutable(dominatingIndexStamp, outdatedIndices, upToDateIndexIds)
    }
  }

  private val dominatingIndexStamp: Long
  private val outdatedIndexIds: IntList
  private val upToDateIndexIds: IntList

  private constructor(dominatingStampIndex: Long,
                      outdatedIndexIds: IntList,
                      upToDateIndexIds: IntList) {
    this.dominatingIndexStamp = dominatingStampIndex
    this.upToDateIndexIds = upToDateIndexIds
    this.outdatedIndexIds = outdatedIndexIds
  }

  internal constructor(indexStamps: Object2LongMap<ID<*, *>>) {
    if (indexStamps.isEmpty()) {
      outdatedIndexIds = IntLists.emptyList()
      upToDateIndexIds = IntLists.emptyList()
      dominatingIndexStamp = 0
    }
    else {
      var dominatingIndexStamp: Long = 0
      outdatedIndexIds = IntArrayList()
      upToDateIndexIds = IntArrayList()

      val entries = ArrayList(indexStamps.object2LongEntrySet())
      entries.sortWith(Comparator.comparingInt { e: Object2LongMap.Entry<ID<*, *>> -> e.key.uniqueId })

      for (entry in entries) {
        var indexStamp = entry.longValue
        if (indexStamp == IndexingStamp.INDEX_DATA_OUTDATED_STAMP) {
          outdatedIndexIds.add(entry.key.uniqueId)
          indexStamp = IndexVersion.getIndexCreationStamp(entry.key)
        }
        else {
          upToDateIndexIds.add(entry.key.uniqueId)
        }
        dominatingIndexStamp = max(dominatingIndexStamp, indexStamp)

        if (IS_UNIT_TEST && indexStamp == IndexingStamp.HAS_NO_INDEXED_DATA_STAMP) {
          FileBasedIndexImpl.LOG.info("Wrong indexing timestamp state: $indexStamps")
        }
      }

      this.dominatingIndexStamp = dominatingIndexStamp
    }
  }

  // Indexed stamp compact format:
  // (DataInputOutputUtil.timeBase + numberOfOutdatedIndices outdated_index_id+)? (dominating_index_stamp) index_id*
  // Note, that FSRecords.REASONABLY_SMALL attribute storage allocation policy will give an attribute 32 bytes to each file
  // Compact format allows 22 indexed states in this state
  @Throws(IOException::class)
  fun writeToStream(stream: DataOutputStream) {
    if (outdatedIndexIds.isEmpty() && upToDateIndexIds.isEmpty()) {
      DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase)
      return
    }

    val numberOfOutdatedIndex = outdatedIndexIds.size
    if (numberOfOutdatedIndex > 0) {
      assert(numberOfOutdatedIndex < ID.MAX_NUMBER_OF_INDICES)
      DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase + numberOfOutdatedIndex)
      outdatedIndexIds.forEach { indexUniqueId ->
        DataInputOutputUtil.writeINT(stream, indexUniqueId)
      }
    }

    DataInputOutputUtil.writeTIME(stream, dominatingIndexStamp)

    upToDateIndexIds.forEach { indexUniqueId ->
      DataInputOutputUtil.writeINT(stream, indexUniqueId)
    }
  }

  fun toMutableTimestamps(): Timestamps {
    val indexStamps = Object2LongOpenHashMap<ID<*, *>>()

    upToDateIndexIds.forEach { indexUniqueId ->
      val id = ID.findById(indexUniqueId)
      if (id != null && id !is StubIndexKey<*, *>) {
        val stamp = IndexVersion.getIndexCreationStamp(id)
        if (stamp != 0L) {
          // All (indices) IDs should be valid in this running session (e.g. we can have ID instance existing but index is not registered)
          if (stamp <= dominatingIndexStamp) {
            indexStamps.put(id, stamp)
          }
        }
      }
    }

    outdatedIndexIds.forEach { outdatedIndexId ->
      val id = ID.findById(outdatedIndexId)
      if (id != null && id !is StubIndexKey<*, *>) {
        if (IndexVersion.getIndexCreationStamp(id) != 0L) {
          // All (indices) IDs should be valid in this running session (e.g. we can have ID instance existing but index is not registered)
          val stamp = IndexingStamp.INDEX_DATA_OUTDATED_STAMP
          if (stamp <= dominatingIndexStamp) {
            indexStamps.put(id, stamp)
          }
        }
      }
    }
    return Timestamps(indexStamps)
  }

  override fun toString(): String {
    return "TimestampsImmutable(dominatingIndexStamp=$dominatingIndexStamp, outdatedIndexIds=$outdatedIndexIds, upToDateIndexIds=$upToDateIndexIds)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as TimestampsImmutable

    if (dominatingIndexStamp != other.dominatingIndexStamp) return false
    if (outdatedIndexIds != other.outdatedIndexIds) return false
    if (upToDateIndexIds != other.upToDateIndexIds) return false

    return true
  }

  override fun hashCode(): Int {
    var result = dominatingIndexStamp.hashCode()
    result = 31 * result + outdatedIndexIds.hashCode()
    result = 31 * result + upToDateIndexIds.hashCode()
    return result
  }

}