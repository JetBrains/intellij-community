// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:ApiStatus.Experimental
package com.intellij.platform.eel.provider.utils

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.fs.ChangeAttributesOptionsBuilder
import com.intellij.platform.eel.fs.EelCopyOption.ProgressEvent
import com.intellij.platform.eel.fs.EelFileInfo
import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryEntryOrder
import com.intellij.platform.eel.fs.EelFileSystemApi.WalkDirectoryOptions.WalkDirectoryTraversalOrder
import com.intellij.platform.eel.fs.EelPosixFileInfoImpl
import com.intellij.platform.eel.fs.StreamingWriteResult
import com.intellij.platform.eel.fs.WalkDirectoryEntry
import com.intellij.platform.eel.fs.WalkDirectoryEntryPosix
import com.intellij.platform.eel.fs.WalkDirectoryEntryResult
import com.intellij.platform.eel.fs.WalkDirectoryEntryWindows
import com.intellij.platform.eel.fs.WalkDirectoryOptionsBuilder
import com.intellij.platform.eel.fs.WriteOptionsBuilder
import com.intellij.platform.eel.fs.listDirectoryWithAttrs
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.isPosix
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.equalsMayBeUsingMachine
import com.intellij.platform.eel.provider.osFamily
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.util.annotations.VisibleToClasses
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedByInterruptException
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.DosFileAttributeView
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.io.path.fileAttributesView
import kotlin.io.path.fileAttributesViewOrNull
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.math.min

@ApiStatus.Internal
object EelPathTransfer {
  private val LOG = Logger.getLogger(EelPathTransfer::class.java.name)

  @Throws(IOException::class)
  fun walkingTransfer(sourceRoot: Path, targetRoot: Path, removeSource: Boolean, copyAttributes: Boolean) {
    val fileAttributesStrategy = if (copyAttributes) EelFileTransferAttributesStrategy.Copy else EelFileTransferAttributesStrategy.Skip
    return walkingTransfer(sourceRoot, targetRoot, removeSource, fileAttributesStrategy)
  }

  @Throws(IOException::class)
  suspend fun walkingTransferSuspend(
    sourceRoot: Path,
    targetRoot: Path,
    removeSource: Boolean,
    copyAttributes: Boolean,
  ) {
    val fileAttributesStrategy = if (copyAttributes) EelFileTransferAttributesStrategy.Copy else EelFileTransferAttributesStrategy.Skip
    walkingTransferSuspend(sourceRoot, targetRoot, removeSource, fileAttributesStrategy)
  }

  @Throws(IOException::class)
  fun walkingTransfer(
    sourceRoot: Path,
    targetRoot: Path,
    removeSource: Boolean,
    fileAttributesStrategy: EelFileTransferAttributesStrategy,
    absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler? = null,
    filter: ((Path) -> Boolean)? = null,
  ) {
    walkingTransfer(
      sourceRoot = sourceRoot,
      targetRoot = targetRoot,
      removeSource = removeSource,
      fileAttributesStrategy = fileAttributesStrategy,
      absoluteSymlinkHandler = absoluteSymlinkHandler,
      filter = filter,
      recursive = true,
      replaceExisting = true,
      continueOnError = false,
      cancellable = false,
      progressFlows = emptyList(),
    )
  }

  @Throws(IOException::class)
  suspend fun walkingTransferSuspend(
    sourceRoot: Path,
    targetRoot: Path,
    removeSource: Boolean,
    fileAttributesStrategy: EelFileTransferAttributesStrategy,
    absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler? = null,
    filter: ((Path) -> Boolean)? = null,
  ) {
    walkingTransferSuspend(
      sourceRoot = sourceRoot,
      targetRoot = targetRoot,
      removeSource = removeSource,
      fileAttributesStrategy = fileAttributesStrategy,
      absoluteSymlinkHandler = absoluteSymlinkHandler,
      filter = filter,
      recursive = true,
      replaceExisting = true,
      continueOnError = false,
      cancellable = true,
      progressFlows = emptyList(),
    )
  }

  @Throws(IOException::class)
  fun walkingTransfer(
    sourceRoot: Path,
    targetRoot: Path,
    removeSource: Boolean,
    fileAttributesStrategy: EelFileTransferAttributesStrategy,
    absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler?,
    filter: ((Path) -> Boolean)?,
    recursive: Boolean,
    replaceExisting: Boolean,
    continueOnError: Boolean,
    cancellable: Boolean,
    progressFlows: List<MutableSharedFlow<ProgressEvent>>,
  ) {
    runTransferBlocking(cancellable = cancellable) {
      walkingTransferSuspend(
        sourceRoot = sourceRoot,
        targetRoot = targetRoot,
        removeSource = removeSource,
        fileAttributesStrategy = fileAttributesStrategy,
        absoluteSymlinkHandler = absoluteSymlinkHandler,
        filter = filter,
        recursive = recursive,
        replaceExisting = replaceExisting,
        continueOnError = continueOnError,
        cancellable = cancellable,
        progressFlows = progressFlows,
      )
    }
  }

  @Throws(IOException::class)
  suspend fun walkingTransferSuspend(
    sourceRoot: Path,
    targetRoot: Path,
    removeSource: Boolean,
    fileAttributesStrategy: EelFileTransferAttributesStrategy,
    absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler?,
    filter: ((Path) -> Boolean)?,
    recursive: Boolean,
    replaceExisting: Boolean,
    continueOnError: Boolean,
    cancellable: Boolean,
    progressFlows: List<MutableSharedFlow<ProgressEvent>>,
  ) {
    if (LOG.isLoggable(Level.FINE)) LOG.fine("walkingTransfer($sourceRoot -> $targetRoot)")
    checkCancellable(cancellable)
    val allSucceeded = incrementalWalkingTransfer(
      sourceRoot = sourceRoot,
      targetRoot = targetRoot,
      fileAttributesStrategy = fileAttributesStrategy,
      absoluteSymlinkHandler = absoluteSymlinkHandler,
      filter = filter,
      recursive = recursive,
      replaceExisting = replaceExisting,
      continueOnError = continueOnError,
      cancellable = cancellable,
      progressFlows = progressFlows,
    )
    checkCancellable(cancellable)
    if (removeSource && allSucceeded) {
      val sourceEel = sourceRoot.asEelPath()
      val targetEel = targetRoot.asEelPath()
      if (!sourceEel.descriptor.equalsMayBeUsingMachine(targetEel.descriptor) || sourceEel.normalize() != targetEel.normalize()) {
        val sourceEelApi = sourceEel.descriptor.toEelApi()
        sourceEelApi.fs.delete(sourceEel, recursive).getOrThrowFileSystemException()
      }
    }
  }

  @Suppress("RAW_RUN_BLOCKING")
  internal fun <T> runTransferBlocking(cancellable: Boolean, block: suspend CoroutineScope.() -> T): T {
    val wasInterrupted = if (cancellable) {
      if (Thread.interrupted()) {
        throw InterruptedException()
      }
      false
    }
    else {
      Thread.interrupted()
    }
    try {
      return runBlocking(block = block)
    }
    finally {
      if (wasInterrupted) {
        Thread.currentThread().interrupt()
      }
    }
  }

  private suspend fun checkCancellable(cancellable: Boolean) {
    if (cancellable) {
      if (Thread.interrupted()) {
        throw InterruptedException()
      }
      currentCoroutineContext().ensureActive()
    }
  }

  private suspend fun rethrowIfInterrupted(cancellable: Boolean, e: IOException) {
    if (cancellable) {
      currentCoroutineContext().ensureActive()
      if (Thread.interrupted() || e is ClosedByInterruptException || e is InterruptedIOException) {
        throw InterruptedException().apply { initCause(e) }
      }
    }
  }


  /**
   * Get individual parts of a relative path.
   * Example: "a/b" -> ["a", "b"]
   */
  @VisibleToClasses(
    "com.intellij.platform.ijent.functional.fs.IncrementalFileTransferTest",
  )
  fun getRelativePathParts(path: Path): List<String> {
    val parts = mutableListOf<String>()
    var current: Path? = path

    while (current != null) {
      val fileName = current.fileName
      if (fileName != null) {
        parts.add(fileName.toString())
      }
      current = current.parent
    }

    return parts.reversed()
  }

  /**
   * Compare individual parts of two relative paths lexicographically.
   * Case-sensitive by default.
   * A shorter component is considered lower compared to a longer component.
   * Example: "a/b" < "ab/b" == True
   */
  private fun compareRelativePathComponents(left: Path, right: Path, ignoreCase: Boolean = false): Int {
    val left = getRelativePathParts(left)
    val right = getRelativePathParts(right)
    for (i in 0 until min(left.size, right.size)) {
      val result = left[i].compareTo(right[i], ignoreCase)
      if (result != 0) {
        return result
      }
    }

    return left.size.compareTo(right.size)
  }

  /**
   * Function only checks permissions, and it ignores the owner, group, sticky bit, gid, and uid.
   * If FileTransferAttributesStrategy is RequirePosixPermissions, it will be checked if target file permissions contain required permissions.
   **/
  private fun arePermissionsEqual(
    fileAttributesStrategy: EelFileTransferAttributesStrategy,
    source: WalkDirectoryEntry.Permissions,
    target: WalkDirectoryEntry.Permissions,
  ): Boolean {
    return when (source) {
      is WalkDirectoryEntryPosix.Permissions -> {
        when (target) {
          is WalkDirectoryEntryPosix.Permissions -> {
            val sourcePermissionsSet = convertMaskToPosixPermissions(source.mask)
            val targetPermissionsSet = convertMaskToPosixPermissions(target.mask)
            when (fileAttributesStrategy) {
              is EelFileTransferAttributesStrategy.RequirePosixPermissions -> (sourcePermissionsSet + fileAttributesStrategy.requiredPermissions) == targetPermissionsSet
              else -> sourcePermissionsSet == targetPermissionsSet
            }
          }
          is WalkDirectoryEntryWindows.Permissions -> {
            // TODO: implement once support for Windows permissions is present in Eel/IJent
            true
          }
        }
      }
      else -> {
        when (target) {
          is WalkDirectoryEntryPosix.Permissions -> {
            when (fileAttributesStrategy) {
              is EelFileTransferAttributesStrategy.RequirePosixPermissions -> false
              else -> true
            }
          }
          is WalkDirectoryEntryWindows.Permissions -> {
            // TODO: implement once support for Windows permissions is present in Eel/IJent
            true
          }
        }
      }
    }
  }

  /**
   * The presence of file timestamps varies by OS and FS. Thus, when comparing timestamps, if a timestamp does not exist on one side,
   * it should not be treated as a mismatch. Creation/birth timestamps are ignored as they cannot be set on POSIX.
   **/
  private fun areTimestampsEqual(left: WalkDirectoryEntry, right: WalkDirectoryEntry): Boolean {
    val isAccessTimeEqual = left.lastAccessTime?.let { sourceTimestamp ->
      right.lastAccessTime?.let { targetTimestamp ->
        sourceTimestamp.compareTo(targetTimestamp) == 0
      }
    } ?: true

    val isModifiedTimeEqual = left.lastModifiedTime?.let { sourceTimestamp ->
      right.lastModifiedTime?.let { targetTimestamp ->
        sourceTimestamp.compareTo(targetTimestamp) == 0
      }
    } ?: true

    // the creation time of a file on posix cannot be set, so if one of the paths is posix, it's treated as equal
    val isCreationTimeEqual = if (left.path.descriptor.osFamily.isPosix || right.path.descriptor.osFamily.isPosix || left.creationTime == null || right.creationTime == null) {
      true
    }
    else {
      left.creationTime?.let { sourceTimestamp ->
        right.creationTime?.let { targetTimestamp ->
          sourceTimestamp.compareTo(targetTimestamp) == 0
        }
      } ?: true
    }

    return isAccessTimeEqual && isModifiedTimeEqual && isCreationTimeEqual
  }

  // NOTE: in the future it could support file system specific file attributes
  private fun areAttributesEqual(left: WalkDirectoryEntry, right: WalkDirectoryEntry): Boolean {
    return when (left) {
      is WalkDirectoryEntryWindows -> when (right) {
        is WalkDirectoryEntryPosix -> {
          true
        }
        is WalkDirectoryEntryWindows -> {
          left.attributes == right.attributes
        }
      }
      else -> {
        true
      }
    }
  }

  @VisibleToClasses(
    "com.intellij.platform.ijent.functional.fs.IncrementalFileTransferTest",
  )
  sealed class DiffOperation {
    // Always syncs permissions, attributes, and timestamps.
    data class Create(
      val sourceFile: WalkDirectoryEntry,
    ) : DiffOperation()

    data class Delete(
      val targetFile: WalkDirectoryEntry,
    ) : DiffOperation()

    data class UpdateMetadata(
      val updatePermissions: Boolean = false,
      val updateAttributes: Boolean = false,
      val updateTimestamps: Boolean = false,
      val sourceFile: WalkDirectoryEntry,
      val targetFile: WalkDirectoryEntry,
    ) : DiffOperation()

    // If a file has the same source and target path but different file type, the existing one is deleted and replaced with a correct one.
    // ReplaceFile is additionally used in the case of a relative symlink. The symlink target cannot be changed in place, thus it requires replacing.
    // Always syncs permissions, attributes and timestamps.
    data class ReplaceFile(
      val sourceFile: WalkDirectoryEntry,
      val targetFile: WalkDirectoryEntry,
    ) : DiffOperation()

    // Always updates timestamps
    data class UpdateContents(
      val updatePermissions: Boolean = false,
      val updateAttributes: Boolean = false,
      val updateTimestamps: Boolean = false,
      val sourceFile: WalkDirectoryEntry,
      val targetFile: WalkDirectoryEntry,
    ) : DiffOperation()

    // Absolute symlinks are left untouched, and it is up to the user-provided lambda to handle it
    internal data class AbsoluteSymlink(
      val sourceSymlink: WalkDirectoryEntry,
      val targetSymlink: WalkDirectoryEntry?,
    ) : DiffOperation()
  }

  /**
   * Function that creates a flow which combines source and target file information.
   * Flow emits [DiffOperation]s that indicate what has to be done to the target file to make it be in sync with the source file.
   **/
  @VisibleToClasses(
    "com.intellij.platform.ijent.functional.fs.IncrementalFileTransferTest",
  )
  fun mergeHashByPath(
    scope: CoroutineScope,
    sourceEntryPoint: Path,
    targetEntryPoint: Path,
    sourceHashFlow: Flow<WalkDirectoryEntryResult>,
    targetHashFlow: Flow<WalkDirectoryEntryResult>,
    fileAttributesStrategy: EelFileTransferAttributesStrategy,
    ignoreCase: Boolean,
    filter: ((Path) -> Boolean)?,
  ): Flow<DiffOperation> =
    mergeHashByPathImpl(
      scope = scope,
      sourceEntryPoint = sourceEntryPoint,
      targetEntryPoint = targetEntryPoint,
      sourceHashFlow = sourceHashFlow,
      targetHashFlow = targetHashFlow,
      fileAttributesStrategy = fileAttributesStrategy,
      ignoreCase = ignoreCase,
      filter = filter,
      replaceExisting = true,
      targetRootExistedBefore = true,
      onExistingConflict = null,
      onUnchangedFile = null,
    )

  private fun mergeHashByPathImpl(
    scope: CoroutineScope,
    sourceEntryPoint: Path,
    targetEntryPoint: Path,
    sourceHashFlow: Flow<WalkDirectoryEntryResult>,
    targetHashFlow: Flow<WalkDirectoryEntryResult>,
    fileAttributesStrategy: EelFileTransferAttributesStrategy,
    ignoreCase: Boolean,
    filter: ((Path) -> Boolean)?,
    replaceExisting: Boolean,
    targetRootExistedBefore: Boolean,
    onExistingConflict: ((WalkDirectoryEntry, WalkDirectoryEntry) -> Unit)?,
    onUnchangedFile: ((WalkDirectoryEntry, WalkDirectoryEntry) -> Unit)?,
  ): Flow<DiffOperation> = flow {
    val sourceHashChan = sourceHashFlow.produceIn(scope)
    val targetHashChan = targetHashFlow.produceIn(scope)

    var sourceEntryResult: WalkDirectoryEntryResult? = null
    var sourceEntry: WalkDirectoryEntry? = null
    var targetEntryResult: WalkDirectoryEntryResult? = null
    var targetEntry: WalkDirectoryEntry? = null

    // walkDirectory does not support filtering, so it will recurse into filtered directories. A way around this problem is to keep track
    // of filtered directories and check if any of them are parents of the current path.
    // The relative paths are stored as components to avoid recomputing the components on each check.
    val filteredParents = mutableListOf<List<String>>()
    val deletedTargetParents = mutableListOf<List<String>>()
    fun isParentFilteredOut(
      path: Path,
      parents: List<List<String>>,
    ): Boolean {
      val pathParts = getRelativePathParts(path)
      return parents.any { parentParts ->
        pathParts.take(parentParts.size) == parentParts
      }
    }

    suspend fun advanceSourceEntry() {
      sourceEntry = null
      sourceEntryResult = null
      while (true) {
        val nextResult = try {
          sourceHashChan.receive()
        }
        catch (_: ClosedReceiveChannelException) {
          break
        }
        val candidate = when (nextResult) {
          is WalkDirectoryEntryResult.Ok -> nextResult.value
          is WalkDirectoryEntryResult.Error -> {
            error("Merge hash by path could not get source entry: ${nextResult.error}")
          }
        }
        val candidateRelPath = candidate.path.asNioPath().relativeTo(sourceEntryPoint)
        if (isParentFilteredOut(candidateRelPath, filteredParents)) {
          continue
        }
        sourceEntryResult = nextResult
        sourceEntry = candidate
        break
      }
    }

    suspend fun advanceTargetEntry() {
      targetEntry = null
      targetEntryResult = null
      while (true) {
        val nextResult = try {
          targetHashChan.receive()
        }
        catch (_: ClosedReceiveChannelException) {
          break
        }
        val candidate = when (nextResult) {
          is WalkDirectoryEntryResult.Ok -> nextResult.value
          is WalkDirectoryEntryResult.Error -> {
            error("Merge hash by path could not get target entry: ${nextResult.error}")
          }
        }
        val candidateRelPath = candidate.path.asNioPath().relativeTo(targetEntryPoint)
        if (isParentFilteredOut(candidateRelPath, deletedTargetParents)) {
          continue
        }
        targetEntryResult = nextResult
        targetEntry = candidate
        break
      }
    }

    advanceSourceEntry()
    advanceTargetEntry()

    while (true) {
      if (LOG.isLoggable(Level.FINER)) LOG.finer("Merge hash by path comparing source: $sourceEntry to target: $targetEntry")
      if (sourceEntry == null && targetEntry == null) {
        break
      }

      // if there is no file on the source side but there is a file on the target side, the file was deleted
      if (sourceEntry == null && targetEntry != null) {
        val currentTarget = targetEntry!!
        if (currentTarget.type is WalkDirectoryEntry.Type.Directory) {
          deletedTargetParents.add(getRelativePathParts(currentTarget.path.asNioPath().relativeTo(targetEntryPoint)))
        }
        advanceTargetEntry()
        if (replaceExisting) {
          when (currentTarget.type) {
            is WalkDirectoryEntry.Type.Other -> error("File of type other should never be yielded")
            else -> emit(DiffOperation.Delete(currentTarget))
          }
        }
        continue
      }

      val currentSource = sourceEntry!!
      val relativeLocalPath = currentSource.path.asNioPath().relativeTo(sourceEntryPoint)
      val shouldTransfer = (filter?.invoke(relativeLocalPath) ?: true) && !isParentFilteredOut(relativeLocalPath, filteredParents)

      // if there is a file on the source side but not on the target side, the file was created
      if (targetEntry == null) {
        if (shouldTransfer) {
          when (currentSource.type) {
            is WalkDirectoryEntry.Type.Symlink.Absolute -> emit(DiffOperation.AbsoluteSymlink(currentSource, null))
            is WalkDirectoryEntry.Type.Other -> error("File of type other should never be yielded")
            else -> emit(DiffOperation.Create(currentSource))
          }
        }
        else {
          when (currentSource.type) {
            is WalkDirectoryEntry.Type.Directory -> filteredParents.add(getRelativePathParts(relativeLocalPath))
            else -> Unit
          }
        }
        advanceSourceEntry()
        continue
      }

      val currentTarget = targetEntry!!
      when (currentSource.type) {
        is WalkDirectoryEntry.Type.Other -> error("File of type other should never be yielded")
        else -> Unit
      }
      when (currentTarget.type) {
        is WalkDirectoryEntry.Type.Other -> error("File of type other should never be yielded")
        else -> Unit
      }

      val relativeRemotePath = currentTarget.path.asNioPath().relativeTo(targetEntryPoint)

      val pathComparison = compareRelativePathComponents(relativeLocalPath, relativeRemotePath, ignoreCase)

      // if the same file is present on both sides, and if the permissions/hash/type is different, sync them
      if (pathComparison == 0) {
        if (shouldTransfer) {
          val isRootPlaceholder = !targetRootExistedBefore && relativeLocalPath.toString().isEmpty()
          val isBothDirectories =
            currentSource.type is WalkDirectoryEntry.Type.Directory && currentTarget.type is WalkDirectoryEntry.Type.Directory
          if (!replaceExisting && !isRootPlaceholder && !isBothDirectories) {
            if (currentSource.type is WalkDirectoryEntry.Type.Directory) {
              filteredParents.add(getRelativePathParts(relativeLocalPath))
            }
            if (currentTarget.type is WalkDirectoryEntry.Type.Directory) {
              deletedTargetParents.add(getRelativePathParts(relativeRemotePath))
            }
            onExistingConflict?.invoke(currentSource, currentTarget)
            advanceSourceEntry()
            advanceTargetEntry()
            continue
          }

          val transferAttributes = when (fileAttributesStrategy) {
            is EelFileTransferAttributesStrategy.Skip -> false
            else -> true
          }
          val updatePermissions =
            transferAttributes && !arePermissionsEqual(fileAttributesStrategy, currentSource.permissions!!, currentTarget.permissions!!)
          val updateAttributes = transferAttributes && !areAttributesEqual(currentSource, currentTarget)
          val updateTimestamps = transferAttributes && !areTimestampsEqual(currentSource, currentTarget)
          var opEmitted = false
          var targetAdvanced = false

          when (currentSource.type) {
            is WalkDirectoryEntry.Type.Directory -> {
              if (currentTarget.type !is WalkDirectoryEntry.Type.Directory) {
                emit(DiffOperation.ReplaceFile(currentSource, currentTarget))
                opEmitted = true
              }
            }
            is WalkDirectoryEntry.Type.Regular -> {
              when (currentTarget.type) {
                is WalkDirectoryEntry.Type.Regular -> {
                  if ((currentSource.type as WalkDirectoryEntry.Type.Regular).hash != (currentTarget.type as WalkDirectoryEntry.Type.Regular).hash) {
                    // updating file contents implies updating modification timestamp
                    emit(DiffOperation.UpdateContents(updatePermissions, updateAttributes, true, currentSource, currentTarget))
                    opEmitted = true
                  }
                }
                else -> {
                  if (currentTarget.type is WalkDirectoryEntry.Type.Directory) {
                    deletedTargetParents.add(getRelativePathParts(relativeRemotePath))
                    advanceTargetEntry()
                    targetAdvanced = true
                  }
                  emit(DiffOperation.ReplaceFile(currentSource, currentTarget))
                  opEmitted = true
                }
              }
            }
            is WalkDirectoryEntry.Type.Symlink.Relative -> {
              when (currentTarget.type) {
                is WalkDirectoryEntry.Type.Symlink.Relative -> {
                  // to be able to compare both relative paths are converted to have the same separator
                  val sourceTarget = (currentSource.type as WalkDirectoryEntry.Type.Symlink.Relative).symlinkRelativePath.replace("\\", "/")
                  val targetTarget = (currentTarget.type as WalkDirectoryEntry.Type.Symlink.Relative).symlinkRelativePath.replace("\\", "/")
                  val areEqual = compareRelativePathComponents(Paths.get(sourceTarget), Paths.get(targetTarget)) == 0
                  if (!areEqual) {
                    emit(DiffOperation.ReplaceFile(currentSource, currentTarget))
                    opEmitted = true
                  }
                }
                else -> {
                  if (currentTarget.type is WalkDirectoryEntry.Type.Directory) {
                    deletedTargetParents.add(getRelativePathParts(relativeRemotePath))
                    advanceTargetEntry()
                    targetAdvanced = true
                  }
                  emit(DiffOperation.ReplaceFile(currentSource, currentTarget))
                  opEmitted = true
                }
              }
            }
            is WalkDirectoryEntry.Type.Symlink.Absolute -> {
              if (currentTarget.type is WalkDirectoryEntry.Type.Directory) {
                deletedTargetParents.add(getRelativePathParts(relativeRemotePath))
                advanceTargetEntry()
                targetAdvanced = true
              }
              emit(DiffOperation.AbsoluteSymlink(currentSource, currentTarget))
              opEmitted = true
            }
            is WalkDirectoryEntry.Type.Other -> {
              // other file types have been handled prior to this when
            }
          }

          // if no other op has been emitted, but there could still be differences in metadata
          if (!opEmitted && (updatePermissions || updateAttributes || updateTimestamps)) {
            when (currentSource.type) {
              // permissions, timestamps, and attributes are generally ignored on symlinks
              is WalkDirectoryEntry.Type.Symlink -> {
                onUnchangedFile?.invoke(currentSource, currentTarget)
              }
              else ->
                emit(DiffOperation.UpdateMetadata(
                  updatePermissions = updatePermissions,
                  updateAttributes = updateAttributes,
                  updateTimestamps = updateTimestamps,
                  sourceFile = currentSource,
                  targetFile = currentTarget,
                ))
            }
          }
          else if (!opEmitted && currentSource.type !is WalkDirectoryEntry.Type.Directory) {
            onUnchangedFile?.invoke(currentSource, currentTarget)
          }

          advanceSourceEntry()
          if (!targetAdvanced) {
            advanceTargetEntry()
          }
        }
        else {
          // if the source file is not to be transferred and the target file exists, the target file should be removed
          when (currentSource.type) {
            is WalkDirectoryEntry.Type.Directory -> filteredParents.add(getRelativePathParts(relativeLocalPath))
            else -> Unit
          }
          if (currentTarget.type is WalkDirectoryEntry.Type.Directory) {
            deletedTargetParents.add(getRelativePathParts(relativeRemotePath))
          }
          advanceSourceEntry()
          advanceTargetEntry()
          if (replaceExisting) {
            emit(DiffOperation.Delete(currentTarget))
          }
        }
      }
      // if the source path is in lower lexicographical order than the target path, it means that the source file was created
      else if (pathComparison < 0) {
        if (shouldTransfer) {
          when (currentSource.type) {
            is WalkDirectoryEntry.Type.Symlink.Absolute -> emit(DiffOperation.AbsoluteSymlink(currentSource, null))
            else -> emit(DiffOperation.Create(currentSource))
          }
        }
        else {
          when (currentSource.type) {
            is WalkDirectoryEntry.Type.Directory -> filteredParents.add(getRelativePathParts(relativeLocalPath))
            else -> Unit
          }
        }
        advanceSourceEntry()
      }
      // if the source path is higher in lexicographical order than the target path, it means that the target file was deleted
      else {
        if (currentTarget.type is WalkDirectoryEntry.Type.Directory) {
          deletedTargetParents.add(getRelativePathParts(relativeRemotePath))
        }
        advanceTargetEntry()
        if (replaceExisting) {
          emit(DiffOperation.Delete(currentTarget))
        }
      }
    }
  }

  fun convertPosixPermissionsToMask(permissions: Set<PosixFilePermission>): Int {
    var mask = 0
    if (PosixFilePermission.OWNER_READ in permissions) mask = mask or 0b100000000
    if (PosixFilePermission.OWNER_WRITE in permissions) mask = mask or 0b010000000
    if (PosixFilePermission.OWNER_EXECUTE in permissions) mask = mask or 0b001000000
    if (PosixFilePermission.GROUP_READ in permissions) mask = mask or 0b000100000
    if (PosixFilePermission.GROUP_WRITE in permissions) mask = mask or 0b000010000
    if (PosixFilePermission.GROUP_EXECUTE in permissions) mask = mask or 0b000001000
    if (PosixFilePermission.OTHERS_READ in permissions) mask = mask or 0b000000100
    if (PosixFilePermission.OTHERS_WRITE in permissions) mask = mask or 0b000000010
    if (PosixFilePermission.OTHERS_EXECUTE in permissions) mask = mask or 0b000000001
    return mask
  }

  internal fun convertMaskToPosixPermissions(mask: Int): Set<PosixFilePermission> {
    val perms = mutableSetOf<PosixFilePermission>()
    if (mask and 0x1 != 0) perms.add(PosixFilePermission.OTHERS_EXECUTE)
    if (mask and 0x2 != 0) perms.add(PosixFilePermission.OTHERS_WRITE)
    if (mask and 0x4 != 0) perms.add(PosixFilePermission.OTHERS_READ)
    if (mask and 0x8 != 0) perms.add(PosixFilePermission.GROUP_EXECUTE)
    if (mask and 0x10 != 0) perms.add(PosixFilePermission.GROUP_WRITE)
    if (mask and 0x20 != 0) perms.add(PosixFilePermission.GROUP_READ)
    if (mask and 0x40 != 0) perms.add(PosixFilePermission.OWNER_EXECUTE)
    if (mask and 0x80 != 0) perms.add(PosixFilePermission.OWNER_WRITE)
    if (mask and 0x100 != 0) perms.add(PosixFilePermission.OWNER_READ)
    return perms
  }

  private suspend fun setPermissionsAndAttributes(
    sourceEntry: WalkDirectoryEntry,
    targetEntry: Path,
    targetEelApi: EelApi,
    fileAttributesStrategy: EelFileTransferAttributesStrategy,
    setPermissions: Boolean,
    setAttributes: Boolean,
    setTimestamps: Boolean,
  ) {
    val attributesOptions = ChangeAttributesOptionsBuilder(targetEntry.asEelPath())

    if (setPermissions) {
      targetEntry.fileAttributesViewOrNull<PosixFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.let { targetView ->
        val perms = mutableSetOf<PosixFilePermission>()

        when (fileAttributesStrategy) {
          is EelFileTransferAttributesStrategy.RequirePosixPermissions -> perms.addAll(fileAttributesStrategy.requiredPermissions)
          else -> Unit
        }

        // NOTE: changing attributes through IJent does not change the owner and the group of a file, so it can be left as zero
        var owner = 0
        var group = 0

        if (sourceEntry.permissions == null) {
          error("Permissions are supposed to be transferred, but were not yielded")
        }
        when (sourceEntry) {
          is WalkDirectoryEntryPosix -> {
            val sourcePerms = sourceEntry.permissions!!
            perms.addAll(convertMaskToPosixPermissions(sourcePerms.mask))
            owner = sourcePerms.owner
            group = sourcePerms.group
          }
          is WalkDirectoryEntryWindows -> {
            perms.addAll(targetView.readAttributes().permissions())
          }
        }
        attributesOptions.permissions(EelPosixFileInfoImpl.Permissions(owner, group, convertPosixPermissionsToMask(perms)))
      }
    }

    if (setAttributes) {
      when (val sourceAttrs = sourceEntry.attributes) {
        is WalkDirectoryEntryPosix.Attributes -> Unit
        is WalkDirectoryEntryWindows.Attributes -> {
          targetEntry.fileAttributesViewOrNull<DosFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.let { targetView ->
            targetView.setHidden(sourceAttrs.isHidden)
            targetView.setSystem(sourceAttrs.isSystem)
            targetView.setArchive(sourceAttrs.isArchive)
            targetView.setReadOnly(sourceAttrs.isReadOnly)
          }
        }
        null -> error("Attributes are supposed to be transferred, but were not yielded")
      }
    }

    if (setTimestamps) {
      sourceEntry.lastModifiedTime?.let { time ->
        val time = time.toInstant()
        val epoch = EelFileSystemApi.timeSinceEpoch(time.epochSecond.toULong(), time.nano.toUInt())
        attributesOptions.modificationTime(epoch)
      }

      sourceEntry.lastAccessTime?.let { time ->
        val time = time.toInstant()
        val epoch = EelFileSystemApi.timeSinceEpoch(time.epochSecond.toULong(), time.nano.toUInt())
        attributesOptions.accessTime(epoch)
      }
    }

    targetEelApi.fs.changeAttributes(attributesOptions.build())
  }

  /**
   * Synchronizes the target directory tree with the source one (directories only).
   * This extra pass is necessary to handle:
   *   - Races when creating parent directories for files
   *   - An edge case where a source directory is deleted and replaced by a file with the same name
   *
   * It also reduces redundant system calls when creating files.
   *
   * [sourceRoot] Has to be a valid path to a directory
   *
   * [targetRoot] Has to be a valid path to a directory
   *
   * [ignoreCase] Whether to ignore case when comparing paths
   *
   * [filter] Lambda that checks whether a given directory is supposed to be transferred. Returns true if the directory should be transferred.
   */
  @VisibleForTesting
  @Throws(IOException::class)
  suspend fun directoryOnlySync(
    sourceRoot: EelPath,
    targetRoot: EelPath,
    ignoreCase: Boolean,
    filter: ((Path) -> Boolean)?,
  ) {
    @Throws(IOException::class)
    suspend fun listDirectories(
      path: Path,
      isLocal: Boolean,
      eelApi: EelApi,
    ): List<Path> =
      if (isLocal) {
        path.listDirectoryEntries()
          .filter { it.isDirectory(LinkOption.NOFOLLOW_LINKS) }
      }
      else {
        eelApi.fs.listDirectoryWithAttrs(path.asEelPath())
          .getOrThrowFileSystemException()
          .filter {
            when (it.second.type) {
              is EelFileInfo.Type.Directory -> true
              else -> false
            }
          }
          .map { path.resolve(it.first) }
      }
        .sortedByDescending { it.pathString }

    val sourceEelApi = sourceRoot.descriptor.toEelApi()
    val targetEelApi = targetRoot.descriptor.toEelApi()
    val isSourceLocal = sourceRoot.descriptor === LocalEelDescriptor
    val isTargetLocal = targetRoot.descriptor === LocalEelDescriptor
    val sourceRoot = sourceRoot.asNioPath()
    val targetRoot = targetRoot.asNioPath()
    var sourceCurrentLayerQ = ArrayDeque<Path>()
    var targetCurrentLayerQ = ArrayDeque<Path>()
    sourceCurrentLayerQ.add(sourceRoot)
    targetCurrentLayerQ.add(targetRoot)

    var sourceNextLayerQ = ArrayDeque<Path>()
    var targetNextLayerQ = ArrayDeque<Path>()

    while (sourceCurrentLayerQ.isNotEmpty() || targetCurrentLayerQ.isNotEmpty()) {
      if (sourceCurrentLayerQ.isNotEmpty() && targetCurrentLayerQ.isEmpty()) {
        val path = sourceCurrentLayerQ.removeFirst()
        val relativeDirPath = path.relativeTo(sourceRoot)
        val targetDirPath = targetRoot.resolve(relativeDirPath)
        val shouldTransfer = filter?.invoke(relativeDirPath) ?: true

        if (shouldTransfer) {
          // edge case when a source file is deleted and replaced by a directory with the same name
          Files.deleteIfExists(targetDirPath)
          Files.createDirectory(targetDirPath)
          sourceNextLayerQ.addAll(listDirectories(path, isSourceLocal, sourceEelApi))
        }
      }
      else if (sourceCurrentLayerQ.isEmpty() && targetCurrentLayerQ.isNotEmpty()) {
        val path = targetCurrentLayerQ.removeFirst()
        targetEelApi.fs.delete(path.asEelPath(), true)
      }
      else {
        val sourceRelativeDirPath = sourceCurrentLayerQ.first().relativeTo(sourceRoot)
        val targetRelativeDirPath = targetCurrentLayerQ.first().relativeTo(targetRoot)
        val comparison = compareRelativePathComponents(sourceRelativeDirPath, targetRelativeDirPath, ignoreCase)

        // new source directory
        if (comparison > 0) {
          val dirTargetPath = targetRoot.resolve(sourceRelativeDirPath)

          val shouldTransfer = filter?.invoke(sourceRelativeDirPath) ?: true
          if (shouldTransfer) {
            // edge case when a source file is deleted and replaced by a directory with the same name
            Files.deleteIfExists(dirTargetPath)
            Files.createDirectory(dirTargetPath)
            sourceNextLayerQ.addAll(listDirectories(sourceCurrentLayerQ.removeFirst(), isSourceLocal, sourceEelApi))
          }
          else {
            sourceCurrentLayerQ.removeFirst()
          }
        }
        // the source directory was deleted
        else if (comparison < 0) {
          val removedTargetDir = targetCurrentLayerQ.removeFirst()
          targetEelApi.fs.delete(removedTargetDir.asEelPath(), true).getOrThrowFileSystemException()
        }
        else {
          val shouldTransfer = filter?.invoke(sourceRelativeDirPath) ?: true
          if (shouldTransfer) {
            sourceNextLayerQ.addAll(listDirectories(sourceCurrentLayerQ.removeFirst(), isSourceLocal, sourceEelApi))
            targetNextLayerQ.addAll(listDirectories(targetCurrentLayerQ.removeFirst(), isTargetLocal, targetEelApi))
          }
          else {
            // if the filter dictates that a source directory should not be transferred and the target directory exists, it should be deleted
            // to maintain the proper directory structure on the target side
            val removedTargetDir = targetCurrentLayerQ.removeFirst()
            targetEelApi.fs.delete(removedTargetDir.asEelPath(), true).getOrThrowFileSystemException()
            sourceCurrentLayerQ.removeFirst()
          }
        }
      }
      if (sourceCurrentLayerQ.isEmpty() && targetCurrentLayerQ.isEmpty()) {
        sourceCurrentLayerQ = sourceNextLayerQ
        targetCurrentLayerQ = targetNextLayerQ
        sourceNextLayerQ = ArrayDeque()
        targetNextLayerQ = ArrayDeque()
      }
    }
  }

  /**
   * Callback invoked for each absolute symlink encountered during transfer.
   * The lambda can do whatever it wants: recreate the symlink, copy contents,
   * ignore it, call [incrementalWalkingTransfer] again on it, or implement any custom logic.
   */
  fun interface IncrementalWalkingTransferAbsoluteSymlinkHandler {
    /**
     * [sourceSymlink] Information about the source symlink
     *
     * [targetEntry] Information about an entry that lives on the path where the target absolute symlink should be. This argument being nullable
     * indicates that something may already exist where the absolute symlink should be placed, and it may or may not be a symlink.
     * If it is null, it means that that nothing exists on that path yet.
     */
    suspend fun handle(sourceSymlink: WalkDirectoryEntry, targetEntry: WalkDirectoryEntry?)
  }

  /**
   * Supports transferring directories, files, and symlinks. [EelFileTransferAttributesStrategy] dictates what attributes are to be
   * transferred (permissions, timestamps, attributes). Relative symlinks are transferred as is, the target path does not have to be
   * valid, and relative symlinks are never followed. Permissions, timestamps, and attributes on symlinks are not transferred as they are
   * ignored by Linux/Unix systems. Absolute symlinks are handled by the user provided [IncrementalWalkingTransferAbsoluteSymlinkHandler].
   *
   * When the source and target are files (either regular files or symbolic links), the file is created if missing, and its timestamps,
   * permissions, and attributes are copied over (unless specifically configured to skip this step). When the source and target are
   * directories, the transfer happens recursively. Any files or directories present in the source but missing from the target are created.
   * Conversely, any files or directories that exist on the target but not in the source are removed. In essence, incremental transfer
   * performs whatever operations are necessary, including deletion and overwriting—to ensure the source and target are completely synchronized.
   *
   * Example:
   * ```
   * Before:
   *
   * (source)
   * a/
   * |- b/
   * |  |- c ("1")
   * |  |- d ("2")
   * |- e ("3")
   *
   * (target)
   * a/
   * |- b/
   * |  |- c ("aaa")
   * |- e/
   * |  | g ("4")
   * |  | h ("5")
   * ```
   * ```
   * After:
   *
   * (target)
   * a/
   * |- b/
   * |  |- c ("1")
   * |  |- d ("2")
   * |- e ("3")
   * ```
   *
   * @throws IOException if a file operation fails, for example when [sourceRoot] does not exist.
   */
  @Throws(IOException::class)
  suspend fun incrementalWalkingTransfer(
    sourceRoot: Path,
    targetRoot: Path,
    fileAttributesStrategy: EelFileTransferAttributesStrategy,
    absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler?,
    filter: ((Path) -> Boolean)?,
  ) {
    incrementalWalkingTransfer(
      sourceRoot = sourceRoot,
      targetRoot = targetRoot,
      fileAttributesStrategy = fileAttributesStrategy,
      absoluteSymlinkHandler = absoluteSymlinkHandler,
      filter = filter,
      recursive = true,
      replaceExisting = true,
      continueOnError = false,
      cancellable = true,
      progressFlows = emptyList(),
    )
  }

  suspend fun incrementalWalkingTransfer(
    sourceRoot: Path,
    targetRoot: Path,
    fileAttributesStrategy: EelFileTransferAttributesStrategy,
    absoluteSymlinkHandler: IncrementalWalkingTransferAbsoluteSymlinkHandler?,
    filter: ((Path) -> Boolean)?,
    recursive: Boolean,
    replaceExisting: Boolean,
    continueOnError: Boolean,
    cancellable: Boolean,
    progressFlows: List<MutableSharedFlow<ProgressEvent>>,
  ): Boolean {
    val totalBytesTransferred = AtomicLong(0L)
    val totalFilesTransferred = AtomicInteger(0)
    val successfulItems = AtomicInteger(0)
    val errors = ConcurrentLinkedQueue<IOException>()
    val progressLock = Any()

    fun emitProgress(event: ProgressEvent) {
      for (flow in progressFlows) {
        if (!flow.tryEmit(event)) {
          val emitBlock: suspend () -> Unit = { flow.emit(event) }
          emitBlock.startCoroutine(Continuation(EmptyCoroutineContext) {})
        }
      }
    }

    fun recordBytesCopied(sourcePath: Path, targetPath: Path, deltaBytes: Long) {
      if (deltaBytes <= 0L) return
      synchronized(progressLock) {
        val bytes = totalBytesTransferred.addAndGet(deltaBytes)
        val files = totalFilesTransferred.get()
        emitProgress(ProgressEvent(sourcePath, targetPath, bytes, files))
      }
    }

    fun recordFileTransferred(sourcePath: Path, targetPath: Path) {
      synchronized(progressLock) {
        val bytes = totalBytesTransferred.get()
        val files = totalFilesTransferred.incrementAndGet()
        successfulItems.incrementAndGet()
        emitProgress(ProgressEvent(sourcePath, targetPath, bytes, files))
      }
    }

    fun emitDirectoryProgress(sourcePath: Path, targetPath: Path) {
      synchronized(progressLock) {
        val bytes = totalBytesTransferred.get()
        val files = totalFilesTransferred.get()
        emitProgress(ProgressEvent(sourcePath, targetPath, bytes, files))
      }
    }

    fun recordDirectoryCreated(sourcePath: Path, targetPath: Path) {
      synchronized(progressLock) {
        val bytes = totalBytesTransferred.get()
        val files = totalFilesTransferred.get()
        successfulItems.incrementAndGet()
        emitProgress(ProgressEvent(sourcePath, targetPath, bytes, files))
      }
    }

    fun recordError(sourcePath: Path, targetPath: Path, error: IOException) {
      synchronized(progressLock) {
        errors.add(error)
        val bytes = totalBytesTransferred.get()
        val files = totalFilesTransferred.get()
        emitProgress(ProgressEvent(sourcePath, targetPath, bytes, files, error))
      }
    }

    val targetRootEel = targetRoot.asEelPath()
    val targetDescriptor = targetRootEel.descriptor
    val targetEelApi = targetDescriptor.toEelApi()
    val sourcePathEel = sourceRoot.asEelPath()
    val sourceDescriptor = sourcePathEel.descriptor
    val sourceOsFamily = sourcePathEel.descriptor.osFamily
    val targetOsFamily = targetRoot.osFamily
    val resolvedSourceRoot = sourcePathEel.asNioPath()
    val resolvedTargetRoot = targetRootEel.asNioPath()

    val sourceAttrs = resolvedSourceRoot.fileAttributesView<BasicFileAttributeView>(LinkOption.NOFOLLOW_LINKS).readAttributes()
    if (sourceDescriptor.equalsMayBeUsingMachine(targetDescriptor)) {
      val normSource = sourcePathEel.normalize()
      val normTarget = targetRootEel.normalize()
      if (normSource == normTarget) {
        return true
      }
      if (sourceAttrs.isDirectory && normTarget.startsWith(normSource)) {
        throw FileSystemException(
          resolvedSourceRoot.toString(),
          resolvedTargetRoot.toString(),
          "Cannot copy or move a directory into its own subdirectory",
        )
      }
    }

    // handle target path not existing
    val targetAttrs = try {
      resolvedTargetRoot.fileAttributesViewOrNull<BasicFileAttributeView>(LinkOption.NOFOLLOW_LINKS)?.readAttributes()
    }
    catch (e: IOException) {
      rethrowIfInterrupted(cancellable, e)
      null
    }
    val targetExistedBefore = targetAttrs != null
    var rootDirectoryCreated = false
    val deferredDirectoryMetadataUpdates = ConcurrentLinkedQueue<DeferredDirectoryMetadataUpdate>()

    coroutineScope {
      when {
        sourceAttrs.isDirectory -> {
          if (targetAttrs == null) {
            Files.createDirectory(resolvedTargetRoot)
            rootDirectoryCreated = true
            emitDirectoryProgress(resolvedSourceRoot, resolvedTargetRoot)
          }
          else if (!targetAttrs.isDirectory) {
            if (!replaceExisting) {
              val err = FileAlreadyExistsException(resolvedTargetRoot.toString())
              recordError(resolvedSourceRoot, resolvedTargetRoot, err)
              throw err
            }
            targetEelApi.fs.delete(targetRootEel, true).getOrThrowFileSystemException()
            Files.createDirectory(resolvedTargetRoot)
            rootDirectoryCreated = true
            emitDirectoryProgress(resolvedSourceRoot, resolvedTargetRoot)
          }
          else if (!replaceExisting && (!continueOnError || !recursive)) {
            val err = FileAlreadyExistsException(resolvedTargetRoot.toString())
            recordError(resolvedSourceRoot, resolvedTargetRoot, err)
            throw err
          }
        }
        sourceAttrs.isRegularFile -> {
          if (targetAttrs != null && !replaceExisting) {
            val err = FileAlreadyExistsException(resolvedTargetRoot.toString())
            recordError(resolvedSourceRoot, resolvedTargetRoot, err)
            throw err
          }
          if (targetAttrs == null) {
            Files.createFile(resolvedTargetRoot)
          }
          else if (!targetAttrs.isRegularFile) {
            targetEelApi.fs.delete(targetRootEel, true).getOrThrowFileSystemException()
            Files.createFile(resolvedTargetRoot)
          }
        }
        sourceAttrs.isSymbolicLink -> {
          if (targetAttrs != null && !replaceExisting) {
            val err = FileAlreadyExistsException(resolvedTargetRoot.toString())
            recordError(resolvedSourceRoot, resolvedTargetRoot, err)
            throw err
          }
          if (targetAttrs == null) {
            // a placeholder file is created so that, in the case of an absolute symlink, the user lambda receives the expected target path (where the absolute symlink should be)
            Files.createFile(resolvedTargetRoot)
          }
          // if targetRoot is a directory, it should be deleted to prevent it from being traversed
          else if (targetAttrs.isDirectory) {
            targetEelApi.fs.delete(targetRootEel, true).getOrThrowFileSystemException()
            // a placeholder file is created so that, in the case of an absolute symlink, the user lambda receives the expected target path (where the absolute symlink should be)
            Files.createFile(resolvedTargetRoot)
          }
        }
      }

      val readMetadata = when (fileAttributesStrategy) {
        is EelFileTransferAttributesStrategy.Skip -> false
        else -> true
      }
      val maxDepth = if (recursive) -1 else 0

      val walkDirectoryOptionsSource = WalkDirectoryOptionsBuilder(sourcePathEel)
        .traversalOrder(WalkDirectoryTraversalOrder.DFS)
        .entryOrder(WalkDirectoryEntryOrder.ALPHABETICAL)
        .yieldOtherFileTypes(false)
        .fileContentsHash(true)
        .readMetadata(readMetadata)
        .maxDepth(maxDepth)
        .build()

      val walkDirectoryOptionsTarget = WalkDirectoryOptionsBuilder(resolvedTargetRoot.asEelPath())
        .traversalOrder(WalkDirectoryTraversalOrder.DFS)
        .entryOrder(WalkDirectoryEntryOrder.ALPHABETICAL)
        .yieldOtherFileTypes(false)
        .fileContentsHash(true)
        .readMetadata(readMetadata)
        .maxDepth(maxDepth)
        .build()

      val sourceHashes = async(Dispatchers.IO) { sourcePathEel.descriptor.toEelApi().fs.walkDirectory(walkDirectoryOptionsSource) }
      val targetHashes = async(Dispatchers.IO) { targetDescriptor.toEelApi().fs.walkDirectory(walkDirectoryOptionsTarget) }

      val semaphore = Semaphore(4) // TODO: fine tune

      mergeHashByPathImpl(
        scope = this,
        sourceEntryPoint = resolvedSourceRoot,
        targetEntryPoint = resolvedTargetRoot,
        sourceHashFlow = sourceHashes.await(),
        targetHashFlow = targetHashes.await(),
        fileAttributesStrategy = fileAttributesStrategy,
        ignoreCase = false,
        filter = filter,
        replaceExisting = replaceExisting,
        targetRootExistedBefore = targetExistedBefore,
        onExistingConflict = { srcEntry, dstEntry ->
          val srcPath = srcEntry.path.asNioPath()
          val dstPath = dstEntry.path.asNioPath()
          val err = FileAlreadyExistsException(dstPath.toString())
          recordError(srcPath, dstPath, err)
          if (!continueOnError) {
            throw err
          }
        },
        onUnchangedFile = { srcEntry, dstEntry ->
          recordFileTransferred(srcEntry.path.asNioPath(), dstEntry.path.asNioPath())
        },
      ).collect { diffOp ->
        checkCancellable(cancellable)

        val directorySourceFile = when (diffOp) {
          is DiffOperation.Create -> diffOp.sourceFile.takeIf { it.type is WalkDirectoryEntry.Type.Directory }
          is DiffOperation.ReplaceFile -> diffOp.sourceFile.takeIf { it.type is WalkDirectoryEntry.Type.Directory }
          else -> null
        }
        if (directorySourceFile != null) {
          val sourceFileNioPath = directorySourceFile.path.asNioPath()
          val relativePath = sourceFileNioPath.relativeTo(resolvedSourceRoot)
          val targetAbsolutePath = resolvedTargetRoot.resolve(relativePath)
          try {
            withContext(Dispatchers.IO) {
              checkCancellable(cancellable)
              if (LOG.isLoggable(Level.FINER)) LOG.finer("Applying directory diff operation: $diffOp")
              if (diffOp is DiffOperation.ReplaceFile) {
                if (diffOp.targetFile.type is WalkDirectoryEntry.Type.Directory) {
                  targetEelApi.fs.delete(diffOp.targetFile.path, true).getOrThrow()
                }
                else {
                  Files.delete(diffOp.targetFile.path.asNioPath())
                }
              }
              Files.createDirectory(targetAbsolutePath)
              when (fileAttributesStrategy) {
                is EelFileTransferAttributesStrategy.Skip -> Unit
                else -> {
                  val setAttributes = sourceOsFamily == EelOsFamily.Windows && targetOsFamily == EelOsFamily.Windows
                  deferredDirectoryMetadataUpdates.add(
                    DeferredDirectoryMetadataUpdate(
                      sourcePathNio = sourceFileNioPath,
                      targetPathNio = targetAbsolutePath,
                      sourceEntry = directorySourceFile,
                      updatePermissions = true,
                      updateAttributes = setAttributes,
                      updateTimestamps = true,
                    )
                  )
                }
              }
            }
            recordDirectoryCreated(sourceFileNioPath, targetAbsolutePath)
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: IOException) {
            rethrowIfInterrupted(cancellable, e)
            recordError(sourceFileNioPath, targetAbsolutePath, e)
            if (!continueOnError) {
              throw e
            }
          }
          return@collect
        }

        // semaphore is used to limit how many files are being synced at any given moment
        semaphore.acquire()
        launch(Dispatchers.IO) {
          var currentSourcePath = resolvedSourceRoot
          var currentTargetPath = resolvedTargetRoot
          try {
            checkCancellable(cancellable)
            if (LOG.isLoggable(Level.FINER)) LOG.finer("Applying diff operation: $diffOp")
            when (diffOp) {
              is DiffOperation.Create, is DiffOperation.ReplaceFile -> {
                val sourceFile = when (diffOp) {
                  is DiffOperation.Create -> diffOp.sourceFile
                  is DiffOperation.ReplaceFile -> diffOp.sourceFile
                }

                val sourceFileNioPath = sourceFile.path.asNioPath()
                val relativePath = sourceFileNioPath.relativeTo(resolvedSourceRoot)
                val targetAbsolutePath = resolvedTargetRoot.resolve(relativePath)
                currentSourcePath = sourceFileNioPath
                currentTargetPath = targetAbsolutePath

                when (diffOp) {
                  is DiffOperation.ReplaceFile -> {
                    if (diffOp.targetFile.type is WalkDirectoryEntry.Type.Directory) {
                      targetEelApi.fs.delete(diffOp.targetFile.path, true).getOrThrow()
                    }
                    else {
                      Files.delete(diffOp.targetFile.path.asNioPath())
                    }
                  }
                  else -> Unit
                }

                when (sourceFile.type) {
                  is WalkDirectoryEntry.Type.Directory -> {
                    error("unreachable, directory creation is handled synchronously before spawning file tasks")
                  }
                  is WalkDirectoryEntry.Type.Regular -> {
                    val targetAbsoluteTempPath = targetAbsolutePath.resolveSibling(targetAbsolutePath.fileName.toString() + ".part")
                    try {
                      emitDirectoryProgress(sourceFileNioPath, targetAbsolutePath)
                      copyRegularFileContents(
                        sourcePathNio = sourceFileNioPath,
                        tempTargetPathNio = targetAbsoluteTempPath,
                        sourceDescriptor = sourceDescriptor,
                        targetDescriptor = targetDescriptor,
                        targetEelApi = targetEelApi,
                        continueOnError = continueOnError,
                        cancellable = cancellable,
                        onBytesCopied = { bytesRead -> recordBytesCopied(sourceFileNioPath, targetAbsolutePath, bytesRead) },
                      )
                      Files.move(targetAbsoluteTempPath, targetAbsolutePath, StandardCopyOption.REPLACE_EXISTING)
                      when (fileAttributesStrategy) {
                        is EelFileTransferAttributesStrategy.Skip -> Unit
                        else -> {
                          // file attributes should only be transferred if both source and target machines are Windows
                          val setAttributes = sourceOsFamily == EelOsFamily.Windows && targetOsFamily == EelOsFamily.Windows
                          setPermissionsAndAttributes(sourceFile,
                                                      targetAbsolutePath,
                                                      targetEelApi,
                                                      fileAttributesStrategy,
                                                      true,
                                                      setAttributes,
                                                      true)
                        }
                      }
                      recordFileTransferred(sourceFileNioPath, targetAbsolutePath)
                    }
                    finally {
                      Files.deleteIfExists(targetAbsoluteTempPath)
                    }
                  }
                  is WalkDirectoryEntry.Type.Symlink.Relative -> {
                    var symlinkTarget = (sourceFile.type as WalkDirectoryEntry.Type.Symlink.Relative).symlinkRelativePath
                    if (sourceOsFamily != targetOsFamily) {
                      symlinkTarget = if (targetOsFamily.isWindows) {
                        symlinkTarget.replace("/", "\\")
                      }
                      else {
                        symlinkTarget.replace("\\", "/")
                      }
                    }
                    Files.createSymbolicLink(targetAbsolutePath, targetAbsolutePath.fileSystem.getPath(symlinkTarget))
                    recordFileTransferred(sourceFileNioPath, targetAbsolutePath)
                    // permissions on symlinks are not applied because they are ignored
                    // TODO: setting timestamps on a symlink requires using ffi syscall in ijent
                    //setPermissionsAndAttributes(sourceFile, targetAbsolutePath, fileAttributesStrategy, false, true, true)
                  }
                  is WalkDirectoryEntry.Type.Symlink.Absolute -> {
                    error("unreachable, absolute symlink should exclusively be handled by the user provided lambda")
                  }
                  is WalkDirectoryEntry.Type.Other -> {
                    // NOTE: other file types not supported
                  }
                }
              }
              is DiffOperation.Delete -> {
                val targetNio = diffOp.targetFile.path.asNioPath()
                currentTargetPath = targetNio
                if (diffOp.targetFile.type is WalkDirectoryEntry.Type.Directory) {
                  targetEelApi.fs.delete(diffOp.targetFile.path, true).getOrThrow()
                }
                else {
                  Files.delete(targetNio)
                }
              }
              is DiffOperation.UpdateContents -> {
                val sourcePathNio = diffOp.sourceFile.path.asNioPath()
                val targetPathNio = diffOp.targetFile.path.asNioPath()
                currentSourcePath = sourcePathNio
                currentTargetPath = targetPathNio
                val tempRemotePath = targetPathNio.resolveSibling(targetPathNio.fileName.toString() + ".part")
                try {
                  emitDirectoryProgress(sourcePathNio, targetPathNio)
                  copyRegularFileContents(
                    sourcePathNio = sourcePathNio,
                    tempTargetPathNio = tempRemotePath,
                    sourceDescriptor = sourceDescriptor,
                    targetDescriptor = targetDescriptor,
                    targetEelApi = targetEelApi,
                    continueOnError = continueOnError,
                    cancellable = cancellable,
                    onBytesCopied = { bytesRead -> recordBytesCopied(sourcePathNio, targetPathNio, bytesRead) },
                  )
                  Files.move(tempRemotePath, targetPathNio, StandardCopyOption.REPLACE_EXISTING)
                  setPermissionsAndAttributes(diffOp.sourceFile,
                                              targetPathNio,
                                              targetEelApi,
                                              fileAttributesStrategy,
                                              diffOp.updatePermissions,
                                              diffOp.updateAttributes,
                                              diffOp.updateTimestamps)
                  recordFileTransferred(sourcePathNio, targetPathNio)
                }
                finally {
                  Files.deleteIfExists(tempRemotePath)
                }
              }
              is DiffOperation.UpdateMetadata -> {
                val sourcePathNio = diffOp.sourceFile.path.asNioPath()
                val targetPathNio = diffOp.targetFile.path.asNioPath()
                currentSourcePath = sourcePathNio
                currentTargetPath = targetPathNio
                when (diffOp.sourceFile.type) {
                  is WalkDirectoryEntry.Type.Symlink -> Unit // permissions, timestamps, and attributes are ignored on symlinks
                  is WalkDirectoryEntry.Type.Directory -> {
                    deferredDirectoryMetadataUpdates.add(
                      DeferredDirectoryMetadataUpdate(
                        sourcePathNio = sourcePathNio,
                        targetPathNio = targetPathNio,
                        sourceEntry = diffOp.sourceFile,
                        updatePermissions = diffOp.updatePermissions,
                        updateAttributes = diffOp.updateAttributes,
                        updateTimestamps = diffOp.updateTimestamps,
                      )
                    )
                  }
                  else -> setPermissionsAndAttributes(diffOp.sourceFile,
                                                      targetPathNio,
                                                      targetEelApi,
                                                      fileAttributesStrategy,
                                                      diffOp.updatePermissions,
                                                      diffOp.updateAttributes,
                                                      diffOp.updateTimestamps)
                }
                if (diffOp.sourceFile.type !is WalkDirectoryEntry.Type.Directory) {
                  recordFileTransferred(sourcePathNio, targetPathNio)
                }
              }
              is DiffOperation.AbsoluteSymlink -> {
                val sourcePathNio = diffOp.sourceSymlink.path.asNioPath()
                val targetPathNio = diffOp.targetSymlink?.path?.asNioPath()
                                    ?: resolvedTargetRoot.resolve(sourcePathNio.relativeTo(resolvedSourceRoot))
                currentSourcePath = sourcePathNio
                currentTargetPath = targetPathNio
                when (absoluteSymlinkHandler) {
                  null -> LOG.info("No absolute symlink handler provided for incremental walking transfer, skipping symlink: ${diffOp.sourceSymlink.path}")
                  else -> {
                    absoluteSymlinkHandler.handle(diffOp.sourceSymlink, diffOp.targetSymlink)
                    recordFileTransferred(sourcePathNio, targetPathNio)
                  }
                }
              }
            }
            checkCancellable(cancellable)
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: IOException) {
            rethrowIfInterrupted(cancellable, e)
            recordError(currentSourcePath, currentTargetPath, e)
            if (!continueOnError) {
              throw e
            }
          }
          finally {
            semaphore.release()
          }
        }
      }
    }

    if (deferredDirectoryMetadataUpdates.isNotEmpty()) {
      withContext(Dispatchers.IO) {
        for (update in deferredDirectoryMetadataUpdates.sortedByDescending { it.targetPathNio.nameCount }) {
          checkCancellable(cancellable)
          try {
            setPermissionsAndAttributes(
              update.sourceEntry,
              update.targetPathNio,
              targetEelApi,
              fileAttributesStrategy,
              update.updatePermissions,
              update.updateAttributes,
              update.updateTimestamps,
            )
          }
          catch (e: CancellationException) {
            throw e
          }
          catch (e: IOException) {
            rethrowIfInterrupted(cancellable, e)
            recordError(update.sourcePathNio, update.targetPathNio, e)
            if (!continueOnError) {
              throw e
            }
          }
        }
      }
    }

    checkCancellable(cancellable)

    if (errors.isEmpty() && successfulItems.get() == 0) {
      if (!replaceExisting && targetExistedBefore) {
        val err = FileAlreadyExistsException(resolvedTargetRoot.toString())
        recordError(resolvedSourceRoot, resolvedTargetRoot, err)
      }
      else if (rootDirectoryCreated) {
        successfulItems.incrementAndGet()
      }
      else {
        recordDirectoryCreated(resolvedSourceRoot, resolvedTargetRoot)
      }
    }

    if (errors.isNotEmpty() && successfulItems.get() == 0) {
      if (!targetExistedBefore) {
        try {
          targetEelApi.fs.delete(targetRootEel, true)
        }
        catch (_: Throwable) {
        }
      }
      val iterator = errors.iterator()
      val primary = iterator.next()
      while (iterator.hasNext()) {
        val suppressed = iterator.next()
        if (suppressed !== primary) {
          primary.addSuppressed(suppressed)
        }
      }
      throw primary
    }

    return errors.isEmpty()
  }

  private data class DeferredDirectoryMetadataUpdate(
    val sourcePathNio: Path,
    val targetPathNio: Path,
    val sourceEntry: WalkDirectoryEntry,
    val updatePermissions: Boolean,
    val updateAttributes: Boolean,
    val updateTimestamps: Boolean,
  )

  private suspend fun copyRegularFileContents(
    sourcePathNio: Path,
    tempTargetPathNio: Path,
    sourceDescriptor: EelDescriptor,
    targetDescriptor: EelDescriptor,
    targetEelApi: EelApi,
    continueOnError: Boolean,
    cancellable: Boolean,
    onBytesCopied: (Long) -> Unit,
  ) {
    if (sourceDescriptor === targetDescriptor) {
      Files.newInputStream(sourcePathNio, READ).use { sourceFile ->
        Files.newOutputStream(tempTargetPathNio, WRITE, TRUNCATE_EXISTING, CREATE).use { targetFile ->
          // this buffer size gave the best overall performance when benchmarking
          val buffer = ByteArray(64 * 1024)
          while (true) {
            checkCancellable(cancellable)
            val bytesRead = sourceFile.read(buffer)
            if (bytesRead < 0) break
            if (bytesRead == 0) continue
            targetFile.write(buffer, 0, bytesRead)
            onBytesCopied(bytesRead.toLong())
          }
        }
      }
    }
    else {
      val opts = WriteOptionsBuilder(tempTargetPathNio.asEelPath())
        .allowCreate()
        .truncateExisting(true)
        .build()

      val chunks = flow {
        FileChannel.open(sourcePathNio, READ).use { chan ->
          while (true) {
            checkCancellable(cancellable)
            // this buffer size gave the best overall performance when benchmarking
            val buffer = ByteBuffer.allocate(256 * 1024)
            val bytesRead = chan.read(buffer)
            if (bytesRead <= 0) break
            buffer.flip()
            emit(buffer)
            onBytesCopied(bytesRead.toLong())
          }
        }
        // buffer size chosen randomly, but intentionally a higher number to have chunks ready at all times
      }.flowOn(Dispatchers.IO).buffer(5)
      val writeRes = targetEelApi.fs.streamingWrite(chunks, opts)
      when (writeRes) {
        is StreamingWriteResult.Error -> {
          val msg = "Streaming write failed writing file a target machine: ${writeRes.error}"
          if (continueOnError) throw IOException(msg) else error(msg)
        }
        is StreamingWriteResult.Ok -> Unit
      }
    }
  }
}