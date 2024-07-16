// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.community.impl.nio

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.ijent.*
import com.intellij.platform.ijent.fs.*
import kotlinx.coroutines.*
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.PathMatcher
import java.nio.file.WatchService
import java.nio.file.attribute.UserPrincipalLookupService
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.startCoroutine

class IjentNioFileSystem internal constructor(
  private val fsProvider: IjentNioFileSystemProvider,
  internal val ijent: FsAndUserApi,
  private val onClose: () -> Unit,
) : FileSystem() {
  /**
   * Just a bunch of parts of [com.intellij.platform.ijent.IjentApi] with proper types. It was introduced to avoid type upcasting.
   */
  internal sealed interface FsAndUserApi {
    val fs: IjentFileSystemApi
    val userInfo: IjentInfo.User

    data class Posix(override val fs: IjentFileSystemPosixApi, override val userInfo: IjentPosixInfo.User) : FsAndUserApi
    data class Windows(override val fs: IjentFileSystemWindowsApi, override val userInfo: IjentWindowsInfo.User) : FsAndUserApi

    companion object {
      fun create(ijentApi: IjentApi): FsAndUserApi = when (ijentApi) {
        is IjentPosixApi -> Posix(ijentApi.fs, ijentApi.info.user)
        is IjentWindowsApi -> Windows(ijentApi.fs, ijentApi.info.user)
      }
    }
  }

  override fun close() {
    onClose()
  }

  override fun provider(): IjentNioFileSystemProvider = fsProvider

  override fun isOpen(): Boolean =
    ijent.fs.coroutineScope.isActive

  override fun isReadOnly(): Boolean = false

  override fun getSeparator(): String =
    when (ijent) {
      is FsAndUserApi.Posix -> "/"
      is FsAndUserApi.Windows -> "\\"
    }

  override fun getRootDirectories(): Iterable<IjentNioPath> = fsBlocking {
    when (val fs = ijent.fs) {
      is IjentFileSystemPosixApi -> listOf(getPath("/"))
      is IjentFileSystemWindowsApi -> fs.getRootDirectories().map { it.toNioPath() }
    }
  }

  override fun getFileStores(): Iterable<FileStore> =
    listOf(IjentNioFileStore(ijent.fs))

  override fun supportedFileAttributeViews(): Set<String> =
    when (ijent) {
      is FsAndUserApi.Windows ->
        setOf("basic", "dos", "acl", "owner", "user")
      is FsAndUserApi.Posix ->
        setOf(
          "basic", "posix", "unix", "owner",
          "user",  // TODO Works only on BSD/macOS.
        )
    }

  override fun getPath(first: String, vararg more: String): IjentNioPath {
    val os = when (ijent) {
      is FsAndUserApi.Posix -> IjentPath.Absolute.OS.UNIX
      is FsAndUserApi.Windows -> IjentPath.Absolute.OS.WINDOWS
    }
    return IjentPath.parse(first, os)
      .getOrThrow()
      .resolve(IjentPath.Relative.build(*more).getOrThrow())
      .getOrThrow()
      .toNioPath()
  }

  override fun getPathMatcher(syntaxAndPattern: String): PathMatcher {
    TODO("Not yet implemented")
  }

  override fun getUserPrincipalLookupService(): UserPrincipalLookupService {
    TODO("Not yet implemented")
  }

  override fun newWatchService(): WatchService {
    TODO("Not yet implemented")
  }

  internal fun <T> fsBlocking(body: suspend () -> T): T = invokeSuspending(body)

  private fun IjentPath.toNioPath(): IjentNioPath =
    IjentNioPath(
      ijentPath = this,
      nioFs = this@IjentNioFileSystem,
    )

  /**
   * Runs a suspending IO operation [block] in non-suspending code.
   * Normally, [kotlinx.coroutines.runBlocking] should be used in such cases,
   * but it has significant performance overhead: creation and installation of an [kotlinx.coroutines.EventLoop].
   *
   * Unfortunately, the execution of [block] may still launch coroutines, although they are very primitive.
   * To mitigate this, we use [Dispatchers.Unconfined] as an elementary event loop.
   * It does not change the final thread of execution,
   * as we are awaiting for a monitor on the same thread where [invokeSuspending] was called.
   *
   * We manage to save up to 30% (300 microseconds) of performance cost in comparison with [kotlinx.coroutines.runBlocking],
   * which is important in case of many short IO operations.
   *
   * The invoked operation is non-cancellable, as one can expect from regular native-based IO calls.
   *
   * @see com.intellij.openapi.application.impl.runSuspend
   */
  private fun <T> invokeSuspending(block: suspend () -> T): T {
    val run = RunSuspend<T>()
    block.startCoroutine(run)
    return run.await()
  }

  private class RunSuspend<T> : Continuation<T> {
    override val context: CoroutineContext = Dispatchers.Unconfined

    var result: Result<T>? = null

    override fun resumeWith(result: Result<T>) = synchronized(this) {
      this.result = result
      @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).notifyAll()
    }

    fun await(): T {
      synchronized(this) {
        while (true) {
          when (val result = this.result) {
            null -> @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") (this as Object).wait()
            else -> {
              return result.getOrThrow() // throw up failure
            }
          }
        }
      }
    }
  }
}