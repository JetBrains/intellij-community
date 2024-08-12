// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl

import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentPosixInfo
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.fs.*
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
suspend fun IjentFailSafeFileSystemPosixApi(
  coroutineScope: CoroutineScope,
  delegateFactory: suspend () -> IjentPosixApi,
): IjentFileSystemApi {
  val holder = DelegateHolder<IjentPosixApi, IjentFileSystemPosixApi>(coroutineScope, delegateFactory)
  val user = holder.withDelegateRetrying { user }
  return IjentFailSafeFileSystemPosixApiImpl(coroutineScope, user, holder)
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
        oldDelegate != null &&
        oldDelegate.isCompleted &&
        oldDelegate.getCompletionExceptionOrNull() == null &&
        oldDelegate.getCompleted().isRunning
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
  override val coroutineScope: CoroutineScope,
  override val user: IjentPosixInfo.User,
  private val holder: DelegateHolder<IjentPosixApi, IjentFileSystemPosixApi>,
) : IjentFileSystemPosixApi {
  override suspend fun userHome(): IjentPath.Absolute? =
    holder.withDelegateRetrying {
      userHome()
    }

  override suspend fun listDirectory(
    path: IjentPath.Absolute,
  ): IjentFsResult<Collection<String>, IjentFileSystemApi.ListDirectoryError> =
    holder.withDelegateRetrying {
      listDirectory(path)
    }

  override suspend fun createDirectory(
    path: IjentPath.Absolute,
    attributes: List<IjentFileSystemPosixApi.CreateDirAttributePosix>,
  ) {
    holder.withDelegateRetrying {
      createDirectory(path, attributes)
    }
  }

  override suspend fun listDirectoryWithAttrs(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean,
  ): IjentFsResult<Collection<Pair<String, IjentPosixFileInfo>>, IjentFileSystemApi.ListDirectoryError> {
    return holder.withDelegateRetrying {
      listDirectoryWithAttrs(path, resolveSymlinks)
    }
  }

  override suspend fun canonicalize(
    path: IjentPath.Absolute,
  ): IjentFsResult<IjentPath.Absolute, IjentFileSystemApi.CanonicalizeError> =
    holder.withDelegateRetrying {
      canonicalize(path)
    }

  override suspend fun stat(
    path: IjentPath.Absolute,
    resolveSymlinks: Boolean,
  ): IjentFsResult<IjentPosixFileInfo, IjentFileSystemApi.StatError> =
    holder.withDelegateRetrying {
      stat(path, resolveSymlinks)
    }

  override suspend fun sameFile(
    source: IjentPath.Absolute,
    target: IjentPath.Absolute,
  ): IjentFsResult<Boolean, IjentFileSystemApi.SameFileError> =
    holder.withDelegateRetrying {
      sameFile(source, target)
    }

  override suspend fun openForReading(
    path: IjentPath.Absolute,
  ): IjentFsResult<IjentOpenedFile.Reader, IjentFileSystemApi.FileReaderError> =
    holder.withDelegateRetrying {
      openForReading(path)
    }

  override suspend fun openForWriting(
    options: IjentFileSystemApi.WriteOptions,
  ): IjentFsResult<IjentOpenedFile.Writer, IjentFileSystemApi.FileWriterError> =
    holder.withDelegateRetrying {
      openForWriting(options)
    }

  override suspend fun openForReadingAndWriting(
    options: IjentFileSystemApi.WriteOptions,
  ): IjentFsResult<IjentOpenedFile.ReaderWriter, IjentFileSystemApi.FileWriterError> =
    holder.withDelegateRetrying {
      openForReadingAndWriting(options)
    }

  override suspend fun delete(path: IjentPath.Absolute, removeContent: Boolean, followLinks: Boolean) {
    holder.withDelegateRetrying {
      delete(path, removeContent, followLinks)
    }
  }

  override suspend fun copy(options: IjentFileSystemApi.CopyOptions) {
    holder.withDelegateRetrying {
      copy(options)
    }
  }

  override suspend fun move(source: IjentPath.Absolute, target: IjentPath.Absolute, replaceExisting: Boolean, followLinks: Boolean) {
    holder.withDelegateRetrying {
      move(source, target, replaceExisting, followLinks)
    }
  }

  override suspend fun createSymbolicLink(target: IjentPath, linkPath: IjentPath.Absolute) {
    holder.withDelegateRetrying {
      createSymbolicLink(target, linkPath)
    }
  }
}