@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IntRef
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLog
import com.intellij.platform.util.io.storages.appendonlylog.AppendOnlyLogFactory
import com.intellij.platform.util.io.storages.intmultimaps.Int2IntMultimap
import com.intellij.util.io.CleanableStorage
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.DurableDataEnumerator
import com.intellij.util.io.IOUtil
import com.intellij.util.io.Unmappable
import com.intellij.util.io.blobstorage.ByteBufferReader
import org.jetbrains.annotations.Nullable
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.function.IntPredicate

internal class DurableStringEnumerator(
  private val valuesLog: AppendOnlyLog,
  private val valueHashToIdFuture: CompletableFuture<Int2IntMultimap>,
) : DurableDataEnumerator<String>, Unmappable, CleanableStorage {
  private val valueHashLock = Any()

  /** lazily initialized in [valueHashToId] */
  private var valueHashToId: Int2IntMultimap? = null

  companion object {
    const val DATA_FORMAT_VERSION: Int = 1
    const val PAGE_SIZE: Int = 8 shl 20

    private val VALUES_LOG_FACTORY = AppendOnlyLogFactory
      .withDefaults()
      .pageSize(PAGE_SIZE)
      .failIfDataFormatVersionNotMatch(DATA_FORMAT_VERSION)
      .checkIfFileCompatibleEagerly(true)
      .cleanIfFileIncompatible()

    //fun open(storagePath: Path): DurableStringEnumerator {
    //  return VALUES_LOG_FACTORY.wrapStorageSafely<DurableStringEnumerator, IOException>(
    //    storagePath,
    //    { valuesLog -> DurableStringEnumerator(valuesLog, buildValueToIdIndex(valuesLog)) }
    //  )
    //}

    fun openAsync(storagePath: Path, executor: AsyncExecutor): DurableStringEnumerator {
      return VALUES_LOG_FACTORY.wrapStorageSafely<DurableStringEnumerator, RuntimeException>(storagePath) { valuesLog ->
        DurableStringEnumerator(valuesLog, executor.execute { buildValueToIdIndex(valuesLog) })
      }
    }
  }

  override fun isDirty(): Boolean = false

  override fun force() {
    valuesLog.flush()
  }

  override fun enumerate(value: String?): Int {
    if (value == null) {
      return DataEnumerator.NULL_ID
    }

    val valueHash = hashOf(value)
    synchronized(valueHashLock) {
      val valueHashToId = valueHashToId()
      val foundId = lookupValue(valueHashToId, value, valueHash)
      if (foundId != DataEnumerator.NULL_ID) {
        return foundId
      }

      val id = writeString(value, valuesLog)
      valueHashToId.put(valueHash, id)
      return id
    }
  }

  override fun tryEnumerate(@Nullable value: String?): Int {
    if (value == null) {
      return DataEnumerator.NULL_ID
    }

    val valueHash = hashOf(value)
    synchronized(valueHashLock) {
      val valueHashToId = valueHashToId()
      return lookupValue(valueHashToId, value, valueHash)
    }
  }

  override fun valueOf(valueId: Int): String? {
    if (!valuesLog.isValidId(valueId.toLong())) {
      return null
    }
    return valuesLog.read(valueId.toLong(), stringReader)
  }

  override fun close() {
    try {
      valueHashToIdFuture.join()
    }
    catch (_: CancellationException) {
    }
    catch (e: Throwable) {
      logger<DurableStringEnumerator>().info(".valueHashToId computation failed", e)
    }

    valuesLog.close()
  }

  override fun closeAndUnsafelyUnmap() {
    close()
    if (valuesLog is Unmappable) {
      valuesLog.closeAndUnsafelyUnmap()
    }
  }

  override fun closeAndClean() {
    close()
    valuesLog.closeAndClean()
  }

  private fun valueHashToId(): Int2IntMultimap {
    var valueHashToId = valueHashToId
    if (valueHashToId == null) {
      valueHashToId = valueHashToIdFuture.get()
      this.valueHashToId = valueHashToId
    }
    return valueHashToId
  }

  private fun lookupValue(valueHashToId: Int2IntMultimap, value: String, hash: Int): Int {
    val foundIdRef = IntRef(DataEnumerator.NULL_ID)
    valueHashToId.lookup(hash, IntPredicate { candidateId ->
      val candidateValue = valuesLog.read(candidateId.toLong(), stringReader)
      if (candidateValue == value) {
        foundIdRef.set(candidateId)
        // stop
        false
      }
      else {
        true
      }
    })
    return foundIdRef.get()
  }
}

private fun hashOf(value: String): Int {
  val hash = value.hashCode()
  if (hash == Int2IntMultimap.NO_VALUE) {
    //Int2IntMultimap doesn't allow 0 keys/values, hence replace 0 hash with just any value!=0. Hash doesn't
    // identify name uniquely anyway, hence this replacement just adds another hash collision -- basically,
    // of collisions
    return -1 // any value!=0 will do
  }
  return hash
}

private val stringReader = ByteBufferReader { readString(it) }

private fun readString(buffer: ByteBuffer): String {
  return IOUtil.readString(buffer)
}

private fun writeString(value: String, valuesLog: AppendOnlyLog): Int {
  val valueBytes = value.encodeToByteArray()
  val appendedId = valuesLog.append(valueBytes)
  return convertLogIdToValueId(appendedId)
}

private fun buildValueToIdIndex(valuesLog: AppendOnlyLog): Int2IntMultimap {
  val valueHashToId = Int2IntMultimap()
  valuesLog.forEachRecord { logId, buffer ->
    val value = readString(buffer)
    val id = convertLogIdToValueId(logId)
    val valueHash = hashOf(value)
    valueHashToId.put(valueHash, id)
    true
  }
  return valueHashToId
}

private fun convertLogIdToValueId(logId: Long): Int = Math.toIntExact(logId)
