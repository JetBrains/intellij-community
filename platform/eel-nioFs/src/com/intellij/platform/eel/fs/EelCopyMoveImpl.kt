// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UseOptimizedEelFunctions")

package com.intellij.platform.eel.fs

import com.intellij.platform.eel.fs.EelCopyOption.ProgressEvent
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.EelFileTransferAttributesStrategy
import com.intellij.platform.eel.provider.utils.EelPathTransfer
import com.intellij.platform.eel.provider.utils.EelPathTransfer.IncrementalWalkingTransferAbsoluteSymlinkHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.io.path.relativeTo

@ApiStatus.Internal
object EelCopyMoveImpl {
  fun hasEelCopyOption(options: Array<out CopyOption?>): Boolean =
    options.any { it is EelCopyOption }

  @Throws(IOException::class)
  fun copy(source: Path, target: Path, vararg options: CopyOption): Path {
    return execute(source = source, target = target, isMove = false, options = options)
  }

  @Throws(IOException::class)
  fun move(source: Path, target: Path, vararg options: CopyOption): Path {
    return execute(source = source, target = target, isMove = true, options = options)
  }

  private fun execute(
    source: Path,
    target: Path,
    isMove: Boolean,
    options: Array<out CopyOption>,
  ): Path {
    var recursive = isMove
    var continueOnError = false
    var replaceExisting = false
    var copyAttributes = isMove
    var atomicMove = false
    var cancellable = false
    val progressFlows = ArrayList<MutableSharedFlow<ProgressEvent>>()

    for (option in options) {
      when (option) {
        is EelCopyOption -> {
          when (option) {
            EelCopyOption.Recursive -> {
              recursive = true
              continueOnError = true
            }
            EelCopyOption.ContinueOnError -> {
              continueOnError = true
            }
            EelCopyOption.Cancellable -> {
              cancellable = true
            }
            is EelCopyOption.Progress -> {
              progressFlows.add(option.flow)
            }
          }
        }
        StandardCopyOption.REPLACE_EXISTING -> {
          replaceExisting = true
        }
        StandardCopyOption.COPY_ATTRIBUTES -> {
          copyAttributes = true
        }
        StandardCopyOption.ATOMIC_MOVE -> {
          if (!isMove) {
            throw UnsupportedOperationException("ATOMIC_MOVE is not supported for copy")
          }
          atomicMove = true
        }
        LinkOption.NOFOLLOW_LINKS -> Unit
        else -> {
          if (option.javaClass.name == "com.sun.nio.file.ExtendedCopyOption" && option.toString() == "INTERRUPTIBLE") {
            cancellable = true
          }
          else {
            throw UnsupportedOperationException("Unsupported copy option: $option")
          }
        }
      }
    }

    if (atomicMove) {
      if (cancellable && Thread.interrupted()) {
        throw InterruptedException()
      }
      if (source.fileSystem != target.fileSystem || source.getEelDescriptor() != target.getEelDescriptor()) {
        throw AtomicMoveNotSupportedException(
          source.toString(),
          target.toString(),
          "Atomic move across different file systems is not supported",
        )
      }
      val attrs = Files.readAttributes(source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
      val moveOpts = if (replaceExisting) {
        arrayOf<CopyOption>(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      }
      else {
        arrayOf<CopyOption>(StandardCopyOption.ATOMIC_MOVE)
      }
      Files.move(source, target, *moveOpts)
      val bytes = if (attrs.isRegularFile) attrs.size() else 0L
      val event = ProgressEvent(source, target, bytes, 1)
      for (flow in progressFlows) {
        if (!flow.tryEmit(event)) {
          val emitBlock: suspend () -> Unit = { flow.emit(event) }
          emitBlock.startCoroutine(Continuation(EmptyCoroutineContext) {})
        }
      }
      return target
    }

    val fileAttributesStrategy =
      if (copyAttributes) EelFileTransferAttributesStrategy.Copy else EelFileTransferAttributesStrategy.Skip

    val absoluteSymlinkHandler = IncrementalWalkingTransferAbsoluteSymlinkHandler { sourceSymlink, targetEntry ->
      val sourceNio = sourceSymlink.path.asNioPath()
      val targetLinkPath = if (targetEntry != null) {
        val existingPath = targetEntry.path.asNioPath()
        Files.deleteIfExists(existingPath)
        existingPath
      }
      else {
        val relativePath = sourceNio.relativeTo(source.asEelPath().asNioPath())
        target.asEelPath().asNioPath().resolve(relativePath)
      }
      val rawTarget = (sourceSymlink.type as WalkDirectoryEntry.Type.Symlink.Absolute).symlinkAbsolutePath.toString()
      val convertedTarget = targetLinkPath.fileSystem.getPath(
        rawTarget.replace(sourceNio.fileSystem.separator, targetLinkPath.fileSystem.separator)
      )
      Files.createSymbolicLink(targetLinkPath, convertedTarget)
    }

    EelPathTransfer.walkingTransfer(
      sourceRoot = source,
      targetRoot = target,
      removeSource = isMove,
      fileAttributesStrategy = fileAttributesStrategy,
      absoluteSymlinkHandler = absoluteSymlinkHandler,
      filter = null,
      recursive = recursive,
      replaceExisting = replaceExisting,
      continueOnError = continueOnError,
      cancellable = cancellable,
      progressFlows = progressFlows,
    )
    return target
  }
}
