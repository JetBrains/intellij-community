// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.CopyOption
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext

/**
 * Additional options for [java.nio.file.Files.copy] and [java.nio.file.Files.move].
 */
@ApiStatus.Experimental
sealed interface EelCopyOption : CopyOption {
  /**
   * Copies or moves directories and their contents recursively.
   *
   * When transferring a directory tree, an [IOException] on an individual file does not abort the entire operation
   * as long as at least one file or directory is transferred. If nothing can be copied or moved, the operation
   * fails with the encountered [IOException].
   */
  object Recursive : EelCopyOption {
    override fun toString(): String = "EelCopyOption.Recursive"
  }

  /**
   * Continues copying or moving remaining files when an individual file fails with an [IOException].
   * Fails the overall operation only if nothing could be copied or moved.
   */
  object ContinueOnError : EelCopyOption {
    override fun toString(): String = "EelCopyOption.ContinueOnError"
  }

  /**
   * Makes the copy or move operation cancellable via the given coroutine [job].
   * When [job] is cancelled, the operation aborts and throws [kotlinx.coroutines.CancellationException].
   */
  class Cancellable(val job: Job) : EelCopyOption {
    constructor(scope: CoroutineScope) : this(scope.coroutineContext.job)
    constructor(context: CoroutineContext) : this(context.job)

    override fun equals(other: Any?): Boolean =
      this === other || (other is Cancellable && job == other.job)

    override fun hashCode(): Int = job.hashCode()

    override fun toString(): String = "EelCopyOption.Cancellable(job=$job)"
  }

  /**
   * Reports copy or move progress events into [flow].
   */
  class Progress(val flow: MutableSharedFlow<ProgressEvent>) : EelCopyOption {
    constructor() : this(
      MutableSharedFlow(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
      )
    )

    override fun equals(other: Any?): Boolean =
      this === other || (other is Progress && flow == other.flow)

    override fun hashCode(): Int = flow.hashCode()

    override fun toString(): String = "EelCopyOption.Progress(flow=$flow)"
  }

  /**
   * A progress snapshot emitted to [Progress.flow] during a copy or move operation.
   */
  class ProgressEvent(
    val source: Path,
    val target: Path,
    val bytesTransferred: Long,
    val filesTransferred: Int,
    val error: IOException?,
  ) {
    constructor(
      source: Path,
      target: Path,
      bytesTransferred: Long,
      filesTransferred: Int,
    ) : this(source, target, bytesTransferred, filesTransferred, null)

    override fun equals(other: Any?): Boolean =
      this === other || (
        other is ProgressEvent &&
        source == other.source &&
        target == other.target &&
        bytesTransferred == other.bytesTransferred &&
        filesTransferred == other.filesTransferred &&
        error == other.error
      )

    override fun hashCode(): Int {
      var result = source.hashCode()
      result = 31 * result + target.hashCode()
      result = 31 * result + bytesTransferred.hashCode()
      result = 31 * result + filesTransferred
      result = 31 * result + (error?.hashCode() ?: 0)
      return result
    }

    override fun toString(): String =
      "ProgressEvent(source=$source, target=$target, bytesTransferred=$bytesTransferred, filesTransferred=$filesTransferred, error=$error)"
  }
}
