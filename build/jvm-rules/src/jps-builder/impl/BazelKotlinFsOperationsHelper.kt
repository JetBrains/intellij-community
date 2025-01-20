@file:Suppress("HardCodedStringLiteral", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.openapi.util.io.FileUtilRt
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.FileProcessor
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.BuildOperations
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.FSOperations.addCompletelyMarkedDirtyTarget
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.kotlin.jps.build.KotlinDirtySourceFilesHolder
import java.io.File

internal class BazelKotlinFsOperationsHelper(
  private val context: CompileContext,
  private val chunk: ModuleChunk,
  private val dirtyFilesHolder: KotlinDirtySourceFilesHolder,
  private val span: Span,
  private val dataManager: BazelBuildDataProvider,
) {
  internal var hasMarkedDirty = false
    private set

  fun markChunk(excludeFiles: Set<File>) {
    val target = chunk.targets.single()
    var completelyMarkedDirty = true
    val stampStorage = dataManager.getFileStampStorage(target)
    for (rootDescriptor in (context.projectDescriptor.buildRootIndex as BazelBuildRootIndex).descriptors) {
      val file = rootDescriptor.rootFile
      val filePath = file.toString()
      if (!(FileUtilRt.extensionEquals(filePath, "kt") || FileUtilRt.extensionEquals(filePath, "kts")) ||
        excludeFiles.contains(file.toFile())) {
        completelyMarkedDirty = false
        continue
      }

      hasMarkedDirty = true

      // if it is a full project rebuild, all storages are already completely cleared;
      // so passing null because there is no need to access the storage to clear non-existing data
      val marker = if (dataManager.isCleanBuild) null else stampStorage
      context.projectDescriptor.fsState.markDirty(context, CompilationRound.NEXT, file, rootDescriptor, marker, false)
    }

    if (completelyMarkedDirty) {
      addCompletelyMarkedDirtyTarget(context, target)
    }
  }

  internal fun markFilesForCurrentRound(files: Sequence<File>) {
    val buildRootIndex = context.projectDescriptor.buildRootIndex as BazelBuildRootIndex
    for (file in files) {
      val root = buildRootIndex.fileToDescriptors.get(file.toPath())
      if (root != null) {
        dirtyFilesHolder.byTarget.get(root.target)?._markDirty(file, root)
      }
    }

    markFilesImpl(files, currentRound = true, span = span) { it.exists() }
  }

  /**
   * Marks given [files] as dirty for current round and given [target] of [chunk].
   */
  fun markFilesForCurrentRound(target: ModuleBuildTarget, files: Collection<File>) {
    require(target in chunk.targets)

    val targetDirtyFiles = dirtyFilesHolder.byTarget.getValue(target)
    val dirtyFileToRoot = HashMap<File, JavaSourceRootDescriptor>()
    for (file in files) {
      val root = context.projectDescriptor.buildRootIndex
        .findAllParentDescriptors<BuildRootDescriptor>(file, context)
        .single { sourceRoot -> sourceRoot.target == target }

      targetDirtyFiles._markDirty(file, root as JavaSourceRootDescriptor)
      dirtyFileToRoot[file] = root
    }

    markFilesImpl(files.asSequence(), currentRound = true, span = span) { it.exists() }
    cleanOutputsForNewDirtyFilesInCurrentRound(target, dirtyFileToRoot)
  }

  private fun cleanOutputsForNewDirtyFilesInCurrentRound(target: ModuleBuildTarget, dirtyFiles: Map<File, JavaSourceRootDescriptor>) {
    val dirtyFilesHolder = object : DirtyFilesHolderBase<JavaSourceRootDescriptor, ModuleBuildTarget>(context) {
      override fun processDirtyFiles(processor: FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>) {
        for ((file, root) in dirtyFiles) {
          processor.apply(target, file, root)
        }
      }

      override fun hasDirtyFiles(): Boolean = dirtyFiles.isNotEmpty()
    }
    BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder)
  }

  fun markFiles(files: Sequence<File>) {
    markFilesImpl(files, currentRound = false, span = span) { it.exists() }
  }

  fun markInChunkOrDependents(files: Sequence<File>, excludeFiles: Set<File>) {
    markFilesImpl(files, currentRound = false, span = span) {
      !excludeFiles.contains(it) && it.exists()
    }
  }

  private inline fun markFilesImpl(
    files: Sequence<File>,
    currentRound: Boolean,
    span: Span,
    shouldMark: (File) -> Boolean
  ) {
    val filesToMark = files.filterTo(HashSet(), shouldMark)
    if (filesToMark.isEmpty()) {
      return
    }

    val compilationRound = if (currentRound) {
      CompilationRound.CURRENT
    }
    else {
      hasMarkedDirty = true
      CompilationRound.NEXT
    }

    for (fileToMark in filesToMark) {
      FSOperations.markDirty(context, compilationRound, fileToMark)
    }
    span.addEvent("mark dirty", Attributes.of(
      AttributeKey.stringArrayKey("filesToMark"), filesToMark.map { it.toString() },
      AttributeKey.stringKey("compilationRound"), compilationRound.name,
    ))
  }
}