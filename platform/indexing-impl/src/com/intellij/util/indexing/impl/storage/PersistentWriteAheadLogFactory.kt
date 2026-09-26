// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.io.storages.circular.CircularBytesBufferOverMMappedFile
import com.intellij.platform.util.io.storages.circular.WriteAheadLogOverCircularBuffer
import com.intellij.util.io.IOUtil
import com.intellij.util.io.IOUtil.KiB
import com.intellij.util.io.IOUtil.MiB
import com.intellij.util.io.writeaheadlog.WriteAheadLog
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ThreadFactory

/** Indexes' pageSize ~= 1Mb: ~63Mb should be enough */
private val DEFAULT_WAL_CAPACITY = CircularBytesBufferOverMMappedFile.Factory.capacityByMaxFileSize(64 * MiB)

@Internal
object PersistentWriteAheadLogFactory {
  private val LOG = logger<PersistentWriteAheadLogFactory>()

  fun setup(
    directory: Path,
    maxInitAttempts: Int = 3,
    walCapacityBytes: Int = DEFAULT_WAL_CAPACITY,
    flusherThreadFactory: ThreadFactory? = null,
    toFileWriter: WriteAheadLog.ToFileWriter,
    invalidateCaches: () -> Unit,
    writeAheadLogOpener: PersistentWriteAheadLogOpener = PersistentWriteAheadLogOpener.DEFAULT,
  ): WriteAheadLog? {
    val bufferPath = directory.resolve("write-ahead-log.wal")
    val enumeratorPath = directory.resolve("write-ahead-log.paths")
    LOG.info("Opening persistent Write-Ahead Log over mmapped file (${directory}/write-ahead-log.{wal,paths})")
    try {
      return openWithRecoveryPolicy(
        bufferPath = bufferPath,
        enumeratorPath = enumeratorPath,
        maxInitAttempts = maxInitAttempts,
        walCapacityBytes = walCapacityBytes,
        flusherThreadFactory = flusherThreadFactory,
        toFileWriter = toFileWriter,
        invalidateCaches = invalidateCaches,
        writeAheadLogOpener = writeAheadLogOpener,
      )
    }
    catch (e: Exception) {
      // Better run this session without WAL than force user to deal with error dialog and restart:
      // WAL is an optional feature that provides some benefits, but running without it is not critical.
      LOG.error("Initialization of persistent Write-Ahead Log is failed -> run without WAL", e)
      return null
    }
  }

  @Throws(IOException::class)
  fun openWithRecoveryPolicy(
    bufferPath: Path,
    enumeratorPath: Path,
    maxInitAttempts: Int,
    walCapacityBytes: Int = DEFAULT_WAL_CAPACITY,
    flusherThreadFactory: ThreadFactory? = null,
    toFileWriter: WriteAheadLog.ToFileWriter,
    invalidateCaches: () -> Unit,
    writeAheadLogOpener: PersistentWriteAheadLogOpener = PersistentWriteAheadLogOpener.DEFAULT,
  ): WriteAheadLog {
    val attemptsFailures = mutableListOf<Throwable>()
    for (attemptNo in 1..maxInitAttempts) {
      try {
        return writeAheadLogOpener.open(bufferPath, enumeratorPath, walCapacityBytes, flusherThreadFactory, toFileWriter)
      }
      catch (e: Throwable) {
        LOG.warn("WriteAheadLog initialization failed (attempt#: $attemptNo) -> try recreating from 0 (+mark indexes as 'corrupted')", e)
        IOUtil.deleteAllFilesStartingWith(bufferPath)
        IOUtil.deleteAllFilesStartingWith(enumeratorPath)

        invalidateCaches()

        attemptsFailures += e
      }
    }

    val ex = IOException(
      "Write-ahead log can't be initialized ($maxInitAttempts attempts failed)",
      attemptsFailures.removeFirst()
    )
    attemptsFailures.forEach { ex.addSuppressed(it) }
    throw ex
  }
}

@Internal
fun interface PersistentWriteAheadLogOpener {
  @Throws(IOException::class)
  fun open(
    bufferPath: Path,
    enumeratorPath: Path,
    walCapacityBytes: Int,
    flusherThreadFactory: ThreadFactory?,
    toFileWriter: WriteAheadLog.ToFileWriter,
  ): WriteAheadLog

  companion object {
    val DEFAULT: PersistentWriteAheadLogOpener = PersistentWriteAheadLogOpener {
      bufferPath,
      enumeratorPath,
      walCapacityBytes,
      flusherThreadFactory,
      toFileWriter ->
      WriteAheadLogOverCircularBuffer.openDefaultWAL(
        bufferPath,
        enumeratorPath,
        walCapacityBytes,
        flusherThreadFactory = flusherThreadFactory,
        toFileWriter = toFileWriter,
      )
    }
  }
}
