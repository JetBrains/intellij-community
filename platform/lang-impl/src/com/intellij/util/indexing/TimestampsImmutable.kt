// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.io.DataInputOutputUtil
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
class TimestampsImmutable(private val indexStamps: Object2LongMap<ID<*, *>>) {

  private interface InputAdapter {
    @Throws(IOException::class)
    fun readTime(): Long

    @Throws(IOException::class)
    fun hasRemaining(): Boolean

    @Throws(IOException::class)
    fun readInt(): Int
  }

  companion object {
    private val IS_UNIT_TEST = ApplicationManager.getApplication().isUnitTestMode()
    @JvmStatic
    @Throws(IOException::class)
    fun readTimestamps(stream: DataInputStream?): TimestampsImmutable {
      if (stream != null) {
        val indexingStamps = readTimestamps(object : InputAdapter {
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
        return TimestampsImmutable(indexingStamps)
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
        val indexingStamps = readTimestamps(object : InputAdapter {
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
        return TimestampsImmutable(indexingStamps)
      }
      else {
        return TimestampsImmutable(Object2LongMaps.emptyMap())
      }
    }


    @Throws(IOException::class)
    private fun readTimestamps(stream: InputAdapter): Object2LongMap<ID<*, *>> {
      val indexStamps = Object2LongOpenHashMap<ID<*, *>>(5, 0.98f)
      var outdatedIndices: IntArray? = null
      //'header' is either timestamp (dominatingIndexStamp), or, if timestamp is small enough
      // (<MAX_SHORT), it is really a number of 'outdatedIndices', followed by actual indices
      // ints (which is index id from ID class), and followed by another timestamp=dominatingIndexStamp
      // value
      var dominatingIndexStamp = stream.readTime()
      val diff = dominatingIndexStamp - DataInputOutputUtil.timeBase
      if (diff > 0 && diff < ID.MAX_NUMBER_OF_INDICES) {
        var numberOfOutdatedIndices = diff.toInt()
        outdatedIndices = IntArray(numberOfOutdatedIndices)
        while (numberOfOutdatedIndices > 0) {
          outdatedIndices[--numberOfOutdatedIndices] = stream.readInt()
        }
        dominatingIndexStamp = stream.readTime()
      }

      //and after is just a set of ints -- Index IDs from ID class
      while (stream.hasRemaining()) {
        //RC: .findById() takes 1/4 of total the method time -- mostly spent on CHMap lookup.
        val id = ID.findById(stream.readInt())
        if (id != null && id !is StubIndexKey<*, *>) {
          val stamp = IndexVersion.getIndexCreationStamp(id)
          if (stamp == 0L) {
            continue  // All (indices) IDs should be valid in this running session (e.g. we can have ID instance existing but index is not registered)
          }
          if (stamp <= dominatingIndexStamp) {
            indexStamps.put(id, stamp)
          }
        }
      }

      if (outdatedIndices != null) {
        for (outdatedIndexId in outdatedIndices) {
          val id = ID.findById(outdatedIndexId)
          if (id != null && id !is StubIndexKey<*, *>) {
            if (IndexVersion.getIndexCreationStamp(id) == 0L) {
              continue  // All (indices) IDs should be valid in this running session (e.g. we can have ID instance existing but index is not registered)
            }
            val stamp = IndexingStamp.INDEX_DATA_OUTDATED_STAMP
            if (stamp <= dominatingIndexStamp) {
              indexStamps.put(id, stamp)
            }
          }
        }
      }

      return indexStamps
    }
  }

  // Indexed stamp compact format:
  // (DataInputOutputUtil.timeBase + numberOfOutdatedIndices outdated_index_id+)? (dominating_index_stamp) index_id*
  // Note, that FSRecords.REASONABLY_SMALL attribute storage allocation policy will give an attribute 32 bytes to each file
  // Compact format allows 22 indexed states in this state
  @Throws(IOException::class)
  fun writeToStream(stream: DataOutputStream) {
    if (indexStamps.isEmpty()) {
      DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase)
      return
    }


    var dominatingIndexStamp: Long = 0
    var numberOfOutdatedIndex: Long = 0
    val entries = ArrayList(indexStamps.object2LongEntrySet())
    entries.sortWith(Comparator.comparingInt { e: Object2LongMap.Entry<ID<*, *>> -> e.key.uniqueId })

    for (entry in entries) {
      var indexStamp = entry.longValue
      if (indexStamp == IndexingStamp.INDEX_DATA_OUTDATED_STAMP) {
        ++numberOfOutdatedIndex
        indexStamp = IndexVersion.getIndexCreationStamp(entry.key)
      }
      dominatingIndexStamp = max(dominatingIndexStamp, indexStamp)

      if (IS_UNIT_TEST && indexStamp == IndexingStamp.HAS_NO_INDEXED_DATA_STAMP) {
        FileBasedIndexImpl.LOG.info("Wrong indexing timestamp state: $indexStamps")
      }
    }

    if (numberOfOutdatedIndex > 0) {
      assert(numberOfOutdatedIndex < ID.MAX_NUMBER_OF_INDICES)
      DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase + numberOfOutdatedIndex)
      for (entry in entries) {
        if (entry.longValue == IndexingStamp.INDEX_DATA_OUTDATED_STAMP) {
          DataInputOutputUtil.writeINT(stream, entry.key.uniqueId)
        }
      }
    }

    DataInputOutputUtil.writeTIME(stream, dominatingIndexStamp)
    for (entry in entries) {
      if (entry.longValue != IndexingStamp.INDEX_DATA_OUTDATED_STAMP) {
        DataInputOutputUtil.writeINT(stream, entry.key.uniqueId)
      }
    }
  }

  fun copyIndexingStamps(): Object2LongMap<ID<*, *>> {
    return Object2LongOpenHashMap(indexStamps)
  }
}