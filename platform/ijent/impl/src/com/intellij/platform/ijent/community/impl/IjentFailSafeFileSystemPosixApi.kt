// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelResult
import com.intellij.platform.eel.EelUserPosixInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemPosixApi
import com.intellij.platform.eel.fs.EelOpenedFile
import com.intellij.platform.eel.fs.EelPosixFileInfo
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.fs.IjentFileSystemApi
import com.intellij.platform.ijent.fs.IjentFileSystemPosixApi
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference

/**
 * A wrapper for [IjentFileSystemApi] that launches a new IJent through [delegateFactory] if an operation
 * with an already created IJent throws [IjentUnavailableException.CommunicationFailure].
 *
 * [delegateFactory] is NOT called if the delegated instance throws [IjentUnavailableException.ClosedByApplication].
 *
 * [delegateFactory] can be called at most once.
 * If the just created new IJent throws [IjentUnavailableException.CommunicationFailure] again, the error is rethrown,
 * but the next attempt to do something with IJent will trigger [delegateFactory] again.
 *
 * [coroutineScope] is used for calling [delegateFactory], but cancellation of [coroutineScope] does NOT close already created
 * instances of [IjentApi].
 *
 * TODO Currently, the implementation retries EVERY operation.
 *  It can become a significant problem for mutating operations, i.e. a data buffer can be hypothetically written into a file
 *  twice if a networking issue happens during the first attempt of writing.
 *  In order to solve this problem, IjentFileSystemApi MUST guarantee idempotency of every call.
 */
@Suppress("FunctionName")
fun IjentFailSafeFileSystemPosixApi(
  coroutineScope: CoroutineScope,
  delegateFactory: suspend () -> IjentPosixApi,
): IjentFileSystemApi {
  val holder = DelegateHolder<IjentPosixApi, IjentFileSystemPosixApi>(coroutineScope, delegateFactory)
  return IjentFailSafeFileSystemPosixApiImpl(holder)
}

private class DelegateHolder<I : IjentApi, F : IjentFileSystemApi>(
  private val coroutineScope: CoroutineScope,
  private val delegateFactory: suspend () -> I,
) {
  private val delegate = AtomicReference<Deferred<I>?>(null)

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun getDelegate(): Deferred<I> =
    delegate.updateAndGet { oldDelegate ->
      if (
        oldDelegate != null && (
          !oldDelegate.isCompleted ||
          oldDelegate.getCompletionExceptionOrNull() == null &&
          oldDelegate.getCompleted().isRunning
        )
      )
        oldDelegate
      else
        coroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
          delegateFactory()
        }
    }!!

  suspend fun <R> withDelegateRetrying(block: suspend F.() -> R): R {
    return try {
      withDelegateFirstAttempt(block)
    }
    catch (err: Throwable) {
      val unwrapped = IjentUnavailableException.unwrapFromCancellationExceptions(err)
      if (unwrapped is IjentUnavailableException.CommunicationFailure) {
        // TODO There must be a request ID, in order to ensure in idempotency of mutating calls.
        withDelegateSecondAttempt(block)
      }
      else {
        throw unwrapped
      }
    }
  }

  /** The function exists just to have a special marker in stacktraces. */
  private suspend fun <R> withDelegateFirstAttempt(block: suspend F.() -> R): R =
    @Suppress("UNCHECKED_CAST") (getDelegate().await().fs as F).block()

  /** The function exists just to have a special marker in stacktraces. */
  private suspend fun <R> withDelegateSecondAttempt(block: suspend F.() -> R): R =
    IjentUnavailableException.unwrapFromCancellationExceptions {
      @Suppress("UNCHECKED_CAST") (getDelegate().await().fs as F).block()
    }
}

/**
 * Unfortunately, [IjentFileSystemApi] is a sealed interface,
 * so implementing a similar class for Windows will require a full copy-paste of this class.
 */
private class IjentFailSafeFileSystemPosixApiImpl(
  private val holder: DelegateHolder<IjentPosixApi, IjentFileSystemPosixApi>,
) : IjentFileSystemPosixApi {
  // TODO Make user suspendable again?
  override val user: EelUserPosixInfo by lazy {
    runBlocking {
      holder.withDelegateRetrying { user }
    }
  }

  override val descriptor: EelDescriptor by lazy {
    runBlocking {
      holder.withDelegateRetrying { descriptor }
    }
  }


  override suspend fun listDirectory(
    path: EelPath,
  ): EelResult<Collection<String>, EelFileSystemApi.ListDirectoryError> =
    holder.withDelegateRetrying {
      listDirectory(path)
    }

  override suspend fun createDirectory(
    path: EelPath,
    attributes: List<EelFileSystemPosixApi.CreateDirAttributePosix>,
  ): EelResult<Unit, EelFileSystemPosixApi.CreateDirectoryError> =
    holder.withDelegateRetrying {
      createDirectory(path, attributes)
    }

  override suspend fun listDirectoryWithAttrs(
    path: EelPath,
    symlinkPolicy: EelFileSystemApi.SymlinkPolicy,
  ): EelResult<Collection<Pair<String, EelPosixFileInfo>>, EelFileSystemApi.ListDirectoryError> {
    return holder.withDelegateRetrying {
      listDirectoryWithAttrs(path, symlinkPolicy)
    }
  }

  override suspend fun canonicalize(
    path: EelPath,
  ): EelResult<EelPath, EelFileSystemApi.CanonicalizeError> =
    holder.withDelegateRetrying {
      canonicalize(path)
    }

  override suspend fun stat(
    path: EelPath,
    symlinkPolicy: EelFileSystemApi.SymlinkPolicy,
  ): EelResult<EelPosixFileInfo, EelFileSystemApi.StatError> =
    holder.withDelegateRetrying {
      stat(path, symlinkPolicy)
    }

  override suspend fun sameFile(
    source: EelPath,
    target: EelPath,
  ): EelResult<Boolean, EelFileSystemApi.SameFileError> =
    holder.withDelegateRetrying {
      sameFile(source, target)
    }

  override suspend fun openForReading(
    path: EelPath,
  ): EelResult<EelOpenedFile.Reader, EelFileSystemApi.FileReaderError> =
    holder.withDelegateRetrying {
      openForReading(path)
    }

  override suspend fun readFully(path: EelPath, limit: ULong, overflowPolicy: EelFileSystemApi.OverflowPolicy): EelResult<EelFileSystemApi.FullReadResult, EelFileSystemApi.FullReadError> = holder.withDelegateRetrying {
    readFully(path, limit, overflowPolicy)
  }

  override suspend fun openForWriting(
    options: EelFileSystemApi.WriteOptions,
  ): EelResult<EelOpenedFile.Writer, EelFileSystemApi.FileWriterError> =
    holder.withDelegateRetrying {
      openForWriting(options)
    }

  override suspend fun openForReadingAndWriting(
    options: EelFileSystemApi.WriteOptions,
  ): EelResult<EelOpenedFile.ReaderWriter, EelFileSystemApi.FileWriterError> =
    holder.withDelegateRetrying {
      openForReadingAndWriting(options)
    }

  override suspend fun delete(path: EelPath, removeContent: Boolean): EelResult<Unit, EelFileSystemApi.DeleteError> =
    holder.withDelegateRetrying {
      delete(path, removeContent)
    }

  override suspend fun copy(options: EelFileSystemApi.CopyOptions): EelResult<Unit, EelFileSystemApi.CopyError> =
    holder.withDelegateRetrying {
      copy(options)
    }

  override suspend fun move(
    source: EelPath,
    target: EelPath,
    replaceExisting: EelFileSystemApi.ReplaceExistingDuringMove,
    followLinks: Boolean,
  ): EelResult<Unit, EelFileSystemApi.MoveError> =
    holder.withDelegateRetrying {
      move(source, target, replaceExisting, followLinks)
    }

  override suspend fun changeAttributes(
    path: EelPath,
    options: EelFileSystemApi.ChangeAttributesOptions,
  ): EelResult<Unit, EelFileSystemApi.ChangeAttributesError> =
    holder.withDelegateRetrying {
      changeAttributes(path, options)
    }

  override suspend fun getDiskInfo(path: EelPath): EelResult<EelFileSystemApi.DiskInfo, EelFileSystemApi.DiskInfoError> {
    return holder.withDelegateRetrying {
      getDiskInfo(path)
    }
  }

  override suspend fun createSymbolicLink(
    target: EelFileSystemPosixApi.SymbolicLinkTarget,
    linkPath: EelPath,
  ): EelResult<Unit, EelFileSystemPosixApi.CreateSymbolicLinkError> =
    holder.withDelegateRetrying {
      createSymbolicLink(target, linkPath)
    }

  override suspend fun createTemporaryDirectory(
    options: EelFileSystemApi.CreateTemporaryEntryOptions,
  ): EelResult<EelPath, EelFileSystemApi.CreateTemporaryEntryError> =
    holder.withDelegateRetrying {
      createTemporaryDirectory(options)
    }

  override suspend fun createTemporaryFile(options: EelFileSystemApi.CreateTemporaryEntryOptions): EelResult<EelPath, EelFileSystemApi.CreateTemporaryEntryError> = holder.withDelegateRetrying {
    createTemporaryFile(options)
  }
}