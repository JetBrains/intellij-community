// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "UnstableApiUsage", "ReplaceGetOrSet", "HardCodedStringLiteral")

package org.jetbrains.bazel.jvm.jps

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.Formats
import com.intellij.tracing.Tracer
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.FileCollectionFactory
import com.intellij.util.containers.FileHashStrategy
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.bazel.jvm.jps.impl.BuildTaskLauncher
import org.jetbrains.bazel.jvm.jps.impl.isBuildChunkAffected
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.builders.*
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl
import org.jetbrains.jps.builders.impl.BuildTargetChunk
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import org.jetbrains.jps.incremental.fs.CompilationRound
import org.jetbrains.jps.incremental.messages.*
import org.jetbrains.jps.incremental.storage.BuildTargetConfiguration
import org.jetbrains.jps.incremental.storage.BuildTargetSourcesState
import org.jetbrains.jps.incremental.storage.SourceToOutputMappingImpl
import org.jetbrains.jps.model.serialization.impl.TimingLog
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.concurrent.Volatile
import kotlin.math.min

private val TARGET_WITH_CLEARED_OUTPUT = GlobalContextKey.create<MutableSet<BuildTarget<*>>>("_targets_with_cleared_output_")
private val LOG = Logger.getInstance(JpsProjectBuilder::class.java)
private val lookup = MethodHandles.lookup()

private const val CLASSPATH_INDEX_FILE_NAME = "classpath.index"

// CLASSPATH_INDEX_FILE_NAME cannot be used because IDEA on run creates CLASSPATH_INDEX_FILE_NAME only if some module class is loaded,
// so, not possible to distinguish case
// "classpath.index doesn't exist because deleted on module file change" vs. "classpath.index doesn't exist because was not created"
private const val UNMODIFIED_MARK_FILE_NAME = ".unmodified"

internal class JpsProjectBuilder(
  private val projectDescriptor: ProjectDescriptor,
  private val builderRegistry: BuilderRegistry,
  private val builderParams: Map<String, String>,
  private val isTestMode: Boolean,
  private val messageHandler: MessageHandler,
) {

  private val totalModuleLevelBuilderCount = builderRegistry.moduleLevelBuilderCount
  private val elapsedTimeNanosByBuilder = ConcurrentHashMap<Builder, AtomicLong>()
  private val numberOfSourcesProcessedByBuilder = ConcurrentHashMap<Builder, AtomicInteger>()

  fun checkRebuildRequired(scope: CompileScope) {
    val rebuildRequiredSpan = Tracer.start("IncProjectBuilder.checkRebuildRequired")
    doCheckRebuildRequired(scope)
    rebuildRequiredSpan.complete()
  }

  suspend fun build(scope: CompileScope) {
    var context: CompileContextImpl? = null
    var sourceState: BuildTargetSourcesState? = null
    try {
      context = createContext(scope)
      sourceState = BuildTargetSourcesState(context)
      val buildSpan = Tracer.start("IncProjectBuilder.runBuild")
      runBuild(context = context)
      buildSpan.complete()
      val dataManager = projectDescriptor.dataManager
      dataManager.saveVersion()
      dataManager.reportUnhandledRelativizerPaths()
      sourceState.reportSourcesState()
      reportRebuiltModules(context)
      reportUnprocessedChanges(context)
    }
    catch (e: StopBuildException) {
      if (context != null) {
        reportRebuiltModules(context)
        reportUnprocessedChanges(context)
      }
      // If build was canceled for some reason, e.g., compilation error, we should report built modules
      sourceState?.reportSourcesState()
      // some builder decided to stop the build
      // report optional progress message if any
      e.message?.takeIf { it.isNotEmpty() }?.let {
        messageHandler.processMessage(ProgressMessage(it))
      }
    }
    catch (e: BuildDataCorruptedException) {
      requestRebuild(e = e, cause = null)
    }
    catch (e: ProjectBuildException) {
      val cause = e.cause
      if (cause is IOException || cause is BuildDataCorruptedException || (cause is RuntimeException && cause.cause is IOException)) {
        requestRebuild(e, cause)
      }
      else {
        // should stop the build with error
        throw e
      }
    }
    finally {
      val finishingCompilationSpan = Tracer.start("finishing compilation")
      context?.projectDescriptor?.dataManager?.flush(false)
      finishingCompilationSpan.complete()
    }
  }

  private fun doCheckRebuildRequired(scope: CompileScope) {
    val isDebugEnabled = LOG.isDebugEnabled
    if (isTestMode) {
      // do not use the heuristic in tests to properly test all cases
      // automatic builds should not cause to start full project rebuilds to avoid situations when user does not expect rebuild
      if (isDebugEnabled) {
        LOG.debug("Rebuild heuristic: skipping the check; isTestMode = true")
      }
      return
    }

    val targetsState = projectDescriptor.targetsState
    val timeThreshold = targetsState.lastSuccessfulRebuildDuration * 95 / 100 // 95% of last registered clean rebuild time
    if (timeThreshold <= 0) {
      if (isDebugEnabled) {
        LOG.debug("Rebuild heuristic: no stats available")
      }
      return
    }

    // check that this is a whole-project incremental build
    // checking only JavaModuleBuildTargetType because these target types directly correspond to project modules
    for (type in JavaModuleBuildTargetType.ALL_TYPES) {
      if (!scope.isBuildIncrementally(type)) {
        if (isDebugEnabled) {
          LOG.debug("Rebuild heuristic: skipping the check because rebuild is forced for targets of type ${type.typeId}")
        }
        return
      }
      if (!scope.isAllTargetsOfTypeAffected(type)) {
        if (isDebugEnabled) {
          LOG.debug("Rebuild heuristic: skipping the check because some targets are excluded from compilation scope, e.g. targets of type ${type.typeId}")
        }
        return
      }
    }

    // compute estimated times for dirty targets
    val allTargetsAffected = HashSet(JavaModuleBuildTargetType.ALL_TYPES)
    val estimatedWorkTime = calculateEstimatedBuildTime(projectDescriptor) { target ->
      // optimization, since we know here that all targets of types JavaModuleBuildTargetType are affected
      allTargetsAffected.contains(target.getTargetType()) || scope.isAffected(target)
    }
    if (isDebugEnabled) {
      LOG.debug("Rebuild heuristic: estimated build time / timeThreshold : $estimatedWorkTime / $timeThreshold")
    }

    if (estimatedWorkTime >= timeThreshold) {
      val message = "Too many modules require recompilation, forcing full project rebuild"
      LOG.info(message)
      LOG.info("Estimated build duration (linear): " + Formats.formatDuration(estimatedWorkTime))
      LOG.info("Last successful rebuild duration (linear): " + Formats.formatDuration(targetsState.lastSuccessfulRebuildDuration))
      LOG.info("Rebuild heuristic time threshold: " + Formats.formatDuration(timeThreshold))
      messageHandler.processMessage(CompilerMessage("", BuildMessage.Kind.INFO, message))
      throw RebuildRequestedException(null)
    }
  }

  private fun requestRebuild(e: Exception, cause: Throwable?) {
    messageHandler.processMessage(CompilerMessage(
      "", BuildMessage.Kind.INFO, "Internal caches are corrupted or have outdated format, forcing project rebuild: $e")
    )
    throw RebuildRequestedException(cause ?: e)
  }

  private suspend fun runBuild(context: CompileContextImpl) {
    context.setDone(0.0f)

    context.addBuildListener(ChainedTargetsBuildListener(context))

    // deletes class loader classpath index files for changed output roots
    context.addBuildListener(object : BuildListener {
      override fun filesGenerated(event: FileGeneratedEvent) {
        val paths = event.paths
        val fs = FileSystems.getDefault()
        if (paths.size == 1) {
          deleteFiles(paths.iterator().next().first, fs)
          return
        }

        val outputs = HashSet<String>()
        for (pair in paths) {
          val root = pair.getFirst()
          if (outputs.add(root)) {
            deleteFiles(root, fs)
          }
        }
      }

      private fun deleteFiles(rootPath: String, fs: FileSystem) {
        val root = fs.getPath(rootPath)
        try {
          Files.deleteIfExists(root.resolve(CLASSPATH_INDEX_FILE_NAME))
          Files.deleteIfExists(root.resolve(UNMODIFIED_MARK_FILE_NAME))
        }
        catch (_: IOException) {
        }
      }
    })
    val allTargetBuilderBuildStartedSpan = Tracer.start("All TargetBuilder.buildStarted")
    for (builder in builderRegistry.targetBuilders) {
      builder.buildStarted(context)
    }
    allTargetBuilderBuildStartedSpan.complete()
    val allModuleLevelBuildersBuildStartedSpan = Tracer.start("All ModuleLevelBuilder.buildStarted")
    for (builder in builderRegistry.moduleLevelBuilders) {
      builder.buildStarted(context)
    }
    allModuleLevelBuildersBuildStartedSpan.complete()

    var buildProgress: BuildProgress? = null
    try {
      buildProgress = BuildProgress(
        projectDescriptor.dataManager,
        projectDescriptor.buildTargetIndex,
        projectDescriptor.buildTargetIndex.getSortedTargetChunks(context),
        Predicate { isBuildChunkAffected(scope = context.scope, chunk = it) }
      )

      // clean roots for targets for which rebuild is forced
      val cleanOutputSourcesSpan = Tracer.start("Clean output sources")
      cleanOutputRoots(context = context)
      cleanOutputSourcesSpan.complete()

      val beforeTasksSpan = Tracer.start("'before' tasks")
      for (task in builderRegistry.beforeTasks) {
        task.build(context)
      }
      TimingLog.LOG.debug("'before' tasks finished")
      beforeTasksSpan.complete()

      val checkingSourcesSpan = Tracer.start("Building targets")
      val buildSpan = Tracer.start("Parallel build")
      BuildTaskLauncher(context, buildProgress, this).buildInParallel()
      buildSpan.complete()
      TimingLog.LOG.debug("Building targets finished")
      checkingSourcesSpan.complete()

      val afterTasksSpan = Tracer.start("'after' span")
      for (task in builderRegistry.afterTasks) {
        task.build(context)
      }
      TimingLog.LOG.debug("'after' tasks finished")
      sendElapsedTimeMessages(context)
      afterTasksSpan.complete()
    }
    finally {
      if (buildProgress != null) {
        buildProgress.updateExpectedAverageTime()
        if (context.isProjectRebuild && !Utils.errorsDetected(context) && !context.cancelStatus.isCanceled) {
          projectDescriptor.targetsState.lastSuccessfulRebuildDuration = buildProgress.absoluteBuildTime
        }
      }
      for (builder in builderRegistry.targetBuilders) {
        builder.buildFinished(context)
      }
      for (builder in builderRegistry.moduleLevelBuilders) {
        builder.buildFinished(context)
      }
    }
  }

  private fun sendElapsedTimeMessages(context: CompileContext) {
    elapsedTimeNanosByBuilder.entries
      .stream()
      .map<BuilderStatisticsMessage?> { entry ->
        val processedSourcesRef = numberOfSourcesProcessedByBuilder.get(entry!!.key)
        val processedSources = processedSourcesRef?.get() ?: 0
        BuilderStatisticsMessage(entry.key.presentableName, processedSources, entry.value.get() / 1000000)
      }
      .sorted(Comparator.comparing<BuilderStatisticsMessage?, String?>(Function { obj: BuilderStatisticsMessage? -> obj!!.builderName }))
      .forEach { buildMessage: BuilderStatisticsMessage? -> context.processMessage(buildMessage) }
  }

  private fun runBuildersForChunk(context: CompileContext, chunk: BuildTargetChunk, buildProgress: BuildProgress): Boolean {
    val targets: Set<BuildTarget<*>> = chunk.targets
    if (targets.size > 1) {
      val moduleTargets = LinkedHashSet<ModuleBuildTarget>()
      for (target in targets) {
        if (target is ModuleBuildTarget) {
          moduleTargets.add(target)
        }
        else {
          val targetsString = targets.joinToString(separator = ", ") { it.presentableName }
          val message = "Cannot build \"${target.presentableName}\" because it is included into a circular dependency ($targetsString))"
          context.processMessage(CompilerMessage("", BuildMessage.Kind.ERROR, message))
          return false
        }
      }

      return runModuleLevelBuilders(wrapWithModuleInfoAppender(context, moduleTargets), ModuleChunk(moduleTargets), buildProgress)
    }

    val target = targets.iterator().next()
    if (target is ModuleBuildTarget) {
      @Suppress("RemoveRedundantQualifierName")
      val mbt = java.util.Set.of(target)
      return runModuleLevelBuilders(wrapWithModuleInfoAppender(context, mbt), ModuleChunk(mbt), buildProgress)
    }

    @Suppress("UNCHECKED_CAST")
    completeRecompiledSourcesSet(context, targets as Collection<BuildTarget<BuildRootDescriptor>>)

    /*
    In general, the set of files corresponding to changed source file may be different
    Need this, for example, to keep up with case changes in file names  for case-insensitive OSes:
    deleting the output before copying is the only way to ensure the case of the output file's name is exactly the same as source file's case
    */
    cleanOldOutputs(context, target)

    val builders = BuilderRegistry.getInstance().targetBuilders
    var builderCount = 0
    for (builder in builders) {
      buildTarget(target, context, builder)
      builderCount++
      buildProgress.updateProgress(target, (builderCount.toDouble()) / builders.size, context)
    }
    return true
  }

  private fun createContext(scope: CompileScope): CompileContextImpl {
    return CompileContextImpl(scope, projectDescriptor, object : MessageHandler {
      override fun processMessage(msg: BuildMessage?) {
        messageHandler.processMessage(msg)
      }
    }, builderParams, CanceledStatus.NULL)
  }

  private suspend fun cleanOutputRoots(context: CompileContext) {
    val cleanStart = System.nanoTime()
    try {
      coroutineScope {
        clearOutputs(context)
      }

      for (type in TargetTypeRegistry.getInstance().targetTypes) {
        if (context.scope.isAllTargetsOfTypeAffected(type)) {
          cleanOutputOfStaleTargets(type, context)
        }
      }
    }
    finally {
      LOG.info("Cleaned output directories in " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cleanStart) + " ms")
    }
  }

  private fun cleanOutputOfStaleTargets(targetType: BuildTargetType<*>, context: CompileContext) {
    val dataManager = projectDescriptor.dataManager
    val targetIds = dataManager.targetsState.getStaleTargetIds(targetType)
    if (targetIds.isEmpty()) {
      return
    }

    context.processMessage(ProgressMessage("Cleaning old output directories\u2026"))
    for (ids in targetIds) {
      val targetId = ids.first!!
      try {
        var mapping: SourceToOutputMappingImpl? = null
        try {
          mapping = dataManager.createSourceToOutputMapForStaleTarget(targetType, targetId)
          clearOutputFiles(context = context, mapping = mapping, targetType = targetType, targetId = ids.second)
        }
        finally {
          mapping?.close()
        }
        dataManager.cleanStaleTarget(targetType, targetId)
      }
      catch (e: IOException) {
        LOG.warn(e)
        messageHandler.processMessage(CompilerMessage("", BuildMessage.Kind.WARNING,
          "Failed to delete output files from obsolete \"$targetId\" target: $e"))
      }
    }
  }

  @Throws(ProjectBuildException::class)
  private fun processDeletedPaths(context: CompileContext, targets: MutableSet<out BuildTarget<*>>): Boolean {
    var doneSomething = false
    try {
      // cleanup outputs
      val targetToRemovedSources: MutableMap<BuildTarget<*>?, MutableCollection<String?>?> = HashMap<BuildTarget<*>?, MutableCollection<String?>?>()

      val dirsToDelete = FileCollectionFactory.createCanonicalFileSet()
      for (target in targets) {
        val deletedPaths = projectDescriptor.fsState.getAndClearDeletedPaths(target)
        if (deletedPaths.isEmpty()) {
          continue
        }

        targetToRemovedSources.put(target, deletedPaths)
        if (isTargetOutputCleared(context, target)) {
          continue
        }
        val buildTargetId = context.projectDescriptor.targetsState.getBuildTargetId(target)
        val shouldPruneEmptyDirs = target is ModuleBasedTarget<*>
        val dataManager = context.projectDescriptor.dataManager
        val sourceToOutputStorage = dataManager.getSourceToOutputMap(target)
        val logger = context.loggingManager.projectBuilderLogger
        // actually delete outputs associated with removed paths
        val pathsForIteration: Collection<String>
        if (isTestMode) {
          // ensure predictable order in test logs
          pathsForIteration = deletedPaths.sorted()
        }
        else {
          pathsForIteration = deletedPaths
        }
        for (deletedSource in pathsForIteration) {
          // deleting outputs corresponding to a non-existing source
          val outputs = sourceToOutputStorage.getOutputs(deletedSource)
          if (outputs != null && !outputs.isEmpty()) {
            val deletedOutputPaths: MutableList<String?> = ArrayList<String?>()
            val outputToSourceRegistry = dataManager.outputToTargetMapping
            for (output in outputToSourceRegistry.removeTargetAndGetSafeToDeleteOutputs(outputs, buildTargetId, sourceToOutputStorage)) {
              val deleted = BuildOperations.deleteRecursively(output, deletedOutputPaths, if (shouldPruneEmptyDirs) dirsToDelete else null)
              if (deleted) {
                doneSomething = true
              }
            }
            if (!deletedOutputPaths.isEmpty()) {
              if (logger.isEnabled) {
                logger.logDeletedFiles(deletedOutputPaths)
              }
              context.processMessage(FileDeletedEvent(deletedOutputPaths))
            }
          }

          if (target is ModuleBuildTarget) {
            // check if the deleted source was associated with a form
            val sourceToFormMap = dataManager.getSourceToFormMap(target)
            val boundForms = sourceToFormMap.getOutputs(deletedSource)
            if (boundForms != null) {
              for (formPath in boundForms) {
                val formFile = File(formPath)
                if (formFile.exists()) {
                  FSOperations.markDirty(context, CompilationRound.CURRENT, formFile)
                }
              }
              sourceToFormMap.remove(deletedSource)
            }
          }
        }
      }
      if (!targetToRemovedSources.isEmpty()) {
        val existing = Utils.REMOVED_SOURCES_KEY.get(context)
        if (existing != null) {
          for (entry in existing.entries) {
            val paths = targetToRemovedSources.get(entry.key)
            if (paths != null) {
              paths.addAll(entry.value!!)
            }
            else {
              targetToRemovedSources.put(entry.key, entry.value)
            }
          }
        }
        Utils.REMOVED_SOURCES_KEY.set(context, targetToRemovedSources)
      }

      FSOperations.pruneEmptyDirs(context, dirsToDelete)
    }
    catch (e: IOException) {
      throw ProjectBuildException(e)
    }
    return doneSomething
  }

  private fun CoroutineScope.clearOutputs(context: CompileContext) {
    val rootsToDelete = HashMap<File, MutableList<BuildTarget<*>>>()
    val allSourceRoots = FileCollectionFactory.createCanonicalFileSet()

    val projectDescriptor = context.projectDescriptor
    val allTargets = projectDescriptor.buildTargetIndex.allTargets
    for (target in allTargets) {
      if (target is ModuleBasedTarget<*>) {
        for (file in target.getOutputRoots(context)) {
          rootsToDelete.computeIfAbsent(file) { ArrayList() }.add(target)
        }
      }
      else if (context.scope.isBuildForced(target)) {
        launch {
          doClearOutputFiles(context, target)
        }
      }
    }

    val moduleIndex = projectDescriptor.moduleExcludeIndex
    for (target in allTargets) {
      for (descriptor in projectDescriptor.buildRootIndex.getTargetRoots(target, context)) {
        // excluding from checks roots with generated sources; because it is safe to delete generated stuff
        if (!descriptor.isGenerated) {
          val rootFile = descriptor.rootFile
          // Some roots aren't marked by as generated, but in fact they are produced by some builder, and it's safe to remove them.
          // However, if a root isn't excluded, it means that its content will be shown in 'Project View'
          // and a user can create new files under it, so it would be dangerous to clean such roots
          if (moduleIndex.isInContent(rootFile)) {
            allSourceRoots.add(rootFile)
          }
        }
      }
    }

    // check that output and source roots are not overlapping
    val compileScope = context.scope
    val filesToDelete = ArrayList<Path>()
    for (entry in rootsToDelete) {
      context.checkCanceled()
      val outputRoot = entry.key
      val rootTargets = entry.value
      val applicability = Applicability.calculate(rootTargets) { compileScope.isBuildForced(it) }
      if (applicability == Applicability.NONE) {
        continue
      }

      // It makes no sense to delete already empty root, but instead it makes sense to clean up the target, because there may exist
      // a directory that has been previously the output root for the target
      var okToDelete = applicability == Applicability.ALL && !isDirEmpty(outputRoot.toPath())
      if (okToDelete && !moduleIndex.isExcluded(outputRoot)) {
        // If the output root itself is directly or indirectly excluded,
        // there cannot be any manageable sources under it.
        // This holds true even if the output root is located within a source root.
        // Therefore, in such cases, it is safe to delete the output root.
        if (JpsPathUtil.isUnder(allSourceRoots, outputRoot)) {
          okToDelete = false
        }
        else {
          val outRootCanonical = FileCollectionFactory.createCanonicalFileSet(listOf(outputRoot))
          for (srcRoot in allSourceRoots) {
            if (JpsPathUtil.isUnder(outRootCanonical, srcRoot)) {
              okToDelete = false
              break
            }
          }
        }
        if (!okToDelete) {
          context.processMessage(CompilerMessage(
            "", BuildMessage.Kind.WARNING,
            "Output path $outputRoot intersects with a source root. Only files that were created by build will be cleaned.")
          )
        }
      }

      if (okToDelete) {
        filesToDelete.add(outputRoot.toPath())
        registerTargetsWithClearedOutput(context, rootTargets)
      }
      else {
        // clean only those files we are aware of
        for (target in rootTargets) {
          if (compileScope.isBuildForced(target)) {
            launch {
              doClearOutputFiles(context, target)
            }
          }
        }
      }
    }

    if (!filesToDelete.isEmpty()) {
      launch {
        var error: Throwable? = null
        for (file in filesToDelete) {
          try {
            FileUtilRt.deleteRecursively(file)
          }
          catch (e: Throwable) {
            if (error == null) {
              error = e
            }
            else {
              error.addSuppressed(e)
            }
          }
        }

        error?.let {
          throw it
        }
      }
    }
  }

  private fun <R : BuildRootDescriptor, T : BuildTarget<R>> buildTarget(target: T, context: CompileContext, builder: TargetBuilder<*, *>) {
    if (builder.getTargetTypes().contains(target.getTargetType())) {
      val holder = object : DirtyFilesHolderBase<R, T>(context) {
        override fun processDirtyFiles(processor: FileProcessor<R, T>) {
          context.projectDescriptor.fsState.processFilesToRecompile(context, target, processor)
        }
      }
      val outputConsumer = BuildOutputConsumerImpl(target, context)
      val start = System.nanoTime()
      @Suppress("UNCHECKED_CAST")
      (builder as TargetBuilder<R, T>).build(target, holder, outputConsumer, context)
      storeBuilderStatistics(builder, System.nanoTime() - start, outputConsumer.numberOfProcessedSources)
      outputConsumer.fireFileGeneratedEvent()
      context.checkCanceled()
    }
  }

  // return true if changed something, false otherwise
  private fun runModuleLevelBuilders(context: CompileContext, chunk: ModuleChunk, buildProgress: BuildProgress): Boolean {
    for (category in BuilderCategory.entries) {
      for (builder in builderRegistry.getBuilders(category)) {
        builder.chunkBuildStarted(context, chunk)
      }
    }

    completeRecompiledSourcesSet(context, chunk.targets)

    var doneSomething = false
    var rebuildFromScratchRequested = false
    var nextPassRequired: Boolean
    val outputConsumer = ChunkBuildOutputConsumerImpl(context)
    try {
      do {
        nextPassRequired = false
        projectDescriptor.fsState.beforeNextRoundStart(context, chunk)

        val dirtyFilesHolder = object : DirtyFilesHolderBase<JavaSourceRootDescriptor, ModuleBuildTarget>(context) {
          override fun processDirtyFiles(processor: FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>) {
            FSOperations.processFilesToRecompile(context, chunk, processor)
          }
        }
        if (!JavaBuilderUtil.isForcedRecompilationAllJavaModules(context.scope)) {
          val cleanedSources = BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder)
          for (entry in cleanedSources.entries) {
            val target: ModuleBuildTarget = entry.key
            val files = entry.value.keys
            if (!files.isEmpty()) {
              val mapping = context.projectDescriptor.dataManager.getSourceToOutputMap(target)
              for (srcFile in files) {
                val outputs = entry.value.get(srcFile)!!
                mapping.setOutputs(srcFile.path, outputs)
                if (!outputs.isEmpty()) {
                  LOG.info("Some outputs were not removed for ${srcFile.path} source file: $outputs")
                }
              }
            }
          }
        }

        try {
          var buildersPassed = 0
          BUILDER_CATEGORY_LOOP@ for (category in BuilderCategory.entries) {
            val builders = builderRegistry.getBuilders(category)
            if (category == BuilderCategory.CLASS_POST_PROCESSOR) {
              // ensure changes from instruments are visible to class post-processors
              saveInstrumentedClasses(outputConsumer)
            }
            if (builders.isEmpty()) {
              continue
            }

            try {
              for (builder in builders) {
                outputConsumer.setCurrentBuilderName(builder.presentableName)
                processDeletedPaths(context, chunk.targets)
                val start = System.nanoTime()
                val processedSourcesBefore = outputConsumer.getNumberOfProcessedSources()
                val buildResult = builder.build(context, chunk, dirtyFilesHolder, outputConsumer)
                storeBuilderStatistics(builder, System.nanoTime() - start, outputConsumer.getNumberOfProcessedSources() - processedSourcesBefore)

                doneSomething = doneSomething or (buildResult != ModuleLevelBuilder.ExitCode.NOTHING_DONE)

                if (buildResult == ModuleLevelBuilder.ExitCode.ABORT) {
                  throw StopBuildException("Builder ${builder.presentableName} requested build stop")
                }
                context.checkCanceled()
                if (buildResult == ModuleLevelBuilder.ExitCode.ADDITIONAL_PASS_REQUIRED) {
                  nextPassRequired = true
                }
                else if (buildResult == ModuleLevelBuilder.ExitCode.CHUNK_REBUILD_REQUIRED) {
                  if (!rebuildFromScratchRequested && !JavaBuilderUtil.isForcedRecompilationAllJavaModules(context.scope)) {
                    notifyChunkRebuildRequested(context, chunk, builder)
                    // allow rebuild from scratch only once per chunk
                    rebuildFromScratchRequested = true
                    try {
                      // forcibly mark all files in the chunk dirty
                      context.projectDescriptor.fsState.clearContextRoundData(context)
                      FSOperations.markDirty(context, CompilationRound.NEXT, chunk, null)
                      // reverting to the beginning
                      nextPassRequired = true
                      outputConsumer.clear()
                      break@BUILDER_CATEGORY_LOOP
                    }
                    catch (e: Exception) {
                      throw ProjectBuildException(e)
                    }
                  }
                  else {
                    LOG.debug("Builder " + builder.presentableName + " requested second chunk rebuild")
                  }
                }

                buildersPassed++
                for (target in chunk.targets) {
                  buildProgress.updateProgress(target, (buildersPassed.toDouble()) / totalModuleLevelBuilderCount, context)
                }
              }
            }
            finally {
              outputConsumer.setCurrentBuilderName(null)
            }
          }
        }
        finally {
          val moreToCompile = JavaBuilderUtil.updateMappingsOnRoundCompletion(context, dirtyFilesHolder, chunk)
          if (moreToCompile) {
            nextPassRequired = true
          }
          JavaBuilderUtil.clearDataOnRoundCompletion(context)
        }
      } while (nextPassRequired)
    }
    finally {
      saveInstrumentedClasses(outputConsumer)
      outputConsumer.fireFileGeneratedEvents()
      outputConsumer.clear()
      for (category in BuilderCategory.entries) {
        for (builder in builderRegistry.getBuilders(category)) {
          builder.chunkBuildFinished(context, chunk)
        }
      }
      if (Utils.errorsDetected(context)) {
        context.processMessage(CompilerMessage("", BuildMessage.Kind.JPS_INFO, "Errors occurred while compiling module ${chunk.presentableShortName}"))
      }
    }

    return doneSomething
  }

  internal fun buildTargetsChunk(context: CompileContext, chunk: BuildTargetChunk, buildProgress: BuildProgress) {
    val buildSpan = Tracer.start(Supplier { "Building ${chunk.presentableName}" })
    val fsState = projectDescriptor.fsState
    var doneSomething: Boolean
    try {
      context.setCompilationStartStamp(chunk.targets, System.currentTimeMillis())

      sendBuildingTargetMessages(chunk.targets, BuildingTargetProgressMessage.Event.STARTED)
      Utils.ERRORS_DETECTED_KEY.set(context, java.lang.Boolean.FALSE)

      for (target in chunk.targets) {
        BuildOperations.ensureFSStateInitialized(context, target, false)
      }

      doneSomething = processDeletedPaths(context, chunk.targets)

      fsState.beforeChunkBuildStart(context, chunk)

      val runBuildersSpan = Tracer.start(Supplier { "runBuilders " + chunk.presentableName })
      doneSomething = doneSomething or runBuildersForChunk(context, chunk, buildProgress)
      runBuildersSpan.complete()

      fsState.clearContextRoundData(context)
      fsState.clearContextChunk(context)

      if (doneSomething) {
        BuildOperations.markTargetsUpToDate(context, chunk)
      }
    }
    catch (e: BuildDataCorruptedException) {
      throw e
    }
    catch (e: ProjectBuildException) {
      throw e
    }
    catch (e: Throwable) {
      val message: @NlsSafe StringBuilder = StringBuilder()
      message.append(chunk.presentableName).append(": ").append(e.javaClass.getName())
      val exceptionMessage = e.message
      if (exceptionMessage != null) {
        message.append(": ").append(exceptionMessage)
      }
      throw ProjectBuildException(message.toString(), e)
    }
    finally {
      buildProgress.onTargetChunkFinished(chunk, context)
      for (rd in context.projectDescriptor.buildRootIndex.clearTempRoots(context)) {
        context.projectDescriptor.fsState.clearRecompile(rd)
      }
      try {
        // restore deleted paths that were not processed by 'integrate'
        val map = Utils.REMOVED_SOURCES_KEY.get(context)
        if (map != null) {
          for (entry in map.entries) {
            val target = entry.key
            val paths = entry.value
            if (paths != null) {
              for (path in paths) {
                fsState.registerDeleted(context, target, File(path), null)
              }
            }
          }
        }
      }
      catch (e: IOException) {
        throw ProjectBuildException(e)
      }
      finally {
        Utils.REMOVED_SOURCES_KEY.set(context, null)
        sendBuildingTargetMessages(chunk.targets, BuildingTargetProgressMessage.Event.FINISHED)
        buildSpan.complete()
      }
    }
  }

  private fun sendBuildingTargetMessages(targets: Set<BuildTarget<*>>, event: BuildingTargetProgressMessage.Event) {
    messageHandler.processMessage(BuildingTargetProgressMessage(targets, event))
  }

  private fun storeBuilderStatistics(builder: Builder, elapsedTime: Long, processedFiles: Int) {
    elapsedTimeNanosByBuilder.computeIfAbsent(builder) { AtomicLong() }.addAndGet(elapsedTime)
    numberOfSourcesProcessedByBuilder.computeIfAbsent(builder) { AtomicInteger() }.addAndGet(processedFiles)
  }
}

private class ChunkBuildOutputConsumerImpl(private val context: CompileContext) : OutputConsumer {
  private val target2Consumer = HashMap<BuildTarget<*>, BuildOutputConsumerImpl>()
  private val classes = HashMap<String, CompiledClass>()
  private val targetToClassesMap = HashMap<BuildTarget<*>, MutableCollection<CompiledClass>>()
  private val outputToBuilderNameMap = Object2ObjectMaps.synchronize(Object2ObjectOpenCustomHashMap<File, String>(FileHashStrategy))

  @Volatile
  private var currentBuilderName: String? = null

  fun setCurrentBuilderName(builderName: String?) {
    currentBuilderName = builderName
  }

  override fun getTargetCompiledClasses(target: BuildTarget<*>): Collection<CompiledClass> {
    return Collections.unmodifiableCollection(targetToClassesMap.get(target) ?: return emptyList())
  }

  override fun getCompiledClasses(): MutableMap<String, CompiledClass> = Collections.unmodifiableMap(classes)

  override fun lookupClassBytes(className: String?): BinaryContent? = classes.get(className)?.content

  override fun registerCompiledClass(target: BuildTarget<*>?, compiled: CompiledClass) {
    val className = compiled.className
    if (className != null) {
      classes.put(className, compiled)
      if (target != null) {
        var classes = targetToClassesMap.get(target)
        if (classes == null) {
          classes = ArrayList<CompiledClass>()
          targetToClassesMap.put(target, classes)
        }
        classes.add(compiled)
      }
    }
    if (target != null) {
      registerOutputFile(target = target, outputFile = compiled.outputFile, sourcePaths = compiled.sourceFilesPaths)
    }
  }

  override fun registerOutputFile(target: BuildTarget<*>, outputFile: File, sourcePaths: MutableCollection<String?>) {
    val currentBuilder = currentBuilderName
    if (currentBuilder != null) {
      val previousBuilder = outputToBuilderNameMap.put(outputFile, currentBuilder)
      if (previousBuilder != null && previousBuilder != currentBuilder) {
        val source = if (sourcePaths.isEmpty()) null else sourcePaths.iterator().next()
        context.processMessage(CompilerMessage(
          currentBuilder, BuildMessage.Kind.ERROR, "Output file \"${outputFile}\" has already been registered by \"$previousBuilder\"", source
        ))
      }
    }
    var consumer = target2Consumer.get(target)
    if (consumer == null) {
      consumer = BuildOutputConsumerImpl(target, context)
      target2Consumer.put(target, consumer)
    }
    consumer.registerOutputFile(outputFile, sourcePaths)
  }

  fun fireFileGeneratedEvents() {
    for (consumer in target2Consumer.values) {
      consumer.fireFileGeneratedEvent()
    }
  }

  fun getNumberOfProcessedSources(): Int {
    var total = 0
    for (consumer in target2Consumer.values) {
      total += consumer.numberOfProcessedSources
    }
    return total
  }

  fun clear() {
    target2Consumer.clear()
    classes.clear()
    targetToClassesMap.clear()
    outputToBuilderNameMap.clear()
  }
}

private class ChainedTargetsBuildListener(private val context: CompileContextImpl) : BuildListener {
  override fun filesGenerated(event: FileGeneratedEvent) {
    val projectDescriptor = this.context.projectDescriptor
    val fsState = projectDescriptor.fsState
    for (pair in event.paths) {
      val relativePath = pair.getSecond() as String
      val file = if (relativePath == ".") File(pair.getFirst()) else File(pair.getFirst(), relativePath)
      for (buildRootDescriptor in projectDescriptor.buildRootIndex.findAllParentDescriptors<BuildRootDescriptor?>(file, this.context)) {
        val target = buildRootDescriptor.target
        if (event.sourceTarget != target) {
          try {
            fsState.markDirty(context, file, buildRootDescriptor, projectDescriptor.dataManager.getFileStampStorage(target), false)
          }
          catch (_: IOException) {
          }
        }
      }
    }
  }

  override fun filesDeleted(event: FileDeletedEvent) {
    val state = context.projectDescriptor.fsState
    val rootIndex = context.projectDescriptor.buildRootIndex
    for (path in event.filePaths) {
      val file = File(FileUtilRt.toSystemDependentName(path))
      for (desc in rootIndex.findAllParentDescriptors<BuildRootDescriptor>(file, context)) {
        state.registerDeleted(context, desc.target, file)
      }
    }
  }
}

private enum class Applicability {
  NONE, PARTIAL, ALL;

  companion object {
    inline fun <T> calculate(collection: Collection<T>, p: (T) -> Boolean): Applicability {
      var count = 0
      var item = 0
      for (elem in collection) {
        item++
        if (p(elem)) {
          count++
          if (item > count) {
            return PARTIAL
          }
        }
        else if (count > 0) {
          return PARTIAL
        }
      }
      return if (count == 0) NONE else ALL
    }
  }
}

private fun calculateEstimatedBuildTime(projectDescriptor: ProjectDescriptor, isAffected: (BuildTarget<*>) -> Boolean): Long {
  val targetsState = projectDescriptor.targetsState
  // compute estimated times for dirty targets
  var estimatedBuildTime = 0L

  val targetIndex = projectDescriptor.buildTargetIndex
  var affectedTargets = 0
  for (target in targetIndex.allTargets) {
    if (!targetIndex.isDummy(target)) {
      val avgTimeToBuild = targetsState.getAverageBuildTime(target.getTargetType())
      if (avgTimeToBuild > 0) {
        // 1. in general case, this time should include dependency analysis and cache update times
        // 2. need to check isAffected() since some targets (like artifacts) may be unaffected even for rebuild
        if (targetsState.getTargetConfiguration(target).isTargetDirty(projectDescriptor) && isAffected(target)) {
          estimatedBuildTime += avgTimeToBuild
          affectedTargets++
        }
      }
    }
  }
  LOG.info("Affected build targets count: $affectedTargets")
  return estimatedBuildTime
}

private fun reportRebuiltModules(context: CompileContextImpl) {
  val modules = BuildTargetConfiguration.MODULES_WITH_TARGET_CONFIG_CHANGED_KEY.get(context)
  if (modules.isNullOrEmpty()) {
    return
  }

  val shown = if (modules.size == 6) 6 else min(5, modules.size)
  val modulesText = modules.stream().limit(shown.toLong()).map { m -> "'" + m.name + "'" }.collect(Collectors.joining(", "))
  val text = JpsBuildBundle.message("build.messages.modules.were.fully.rebuilt", modulesText, modules.size,
    modules.size - shown, if (ModuleBuildTarget.REBUILD_ON_DEPENDENCY_CHANGE) 1 else 0)
  context.processMessage(CompilerMessage("", BuildMessage.Kind.INFO, text))
}

private fun reportUnprocessedChanges(context: CompileContextImpl) {
  val pd = context.projectDescriptor
  val fsState = pd.fsState
  for (target in pd.buildTargetIndex.allTargets) {
    if (fsState.hasUnprocessedChanges(context, target)) {
      context.processMessage(UnprocessedFSChangesNotification())
      break
    }
  }
}

internal fun clearOutputFiles(context: CompileContext, target: BuildTarget<*>) {
  val map = context.projectDescriptor.dataManager.getSourceToOutputMap(target)
  val targetType = target.getTargetType()
  clearOutputFiles(
    context = context,
    mapping = map,
    targetType = targetType,
    targetId = context.projectDescriptor.dataManager.targetsState.getBuildTargetId(target)
  )
  registerTargetsWithClearedOutput(context, listOf(target))
}

private fun registerTargetsWithClearedOutput(context: CompileContext, targets: Collection<BuildTarget<*>>) {
  synchronized(TARGET_WITH_CLEARED_OUTPUT) {
    var data = context.getUserData(TARGET_WITH_CLEARED_OUTPUT)
    if (data == null) {
      data = HashSet<BuildTarget<*>>()
      context.putUserData(TARGET_WITH_CLEARED_OUTPUT, data)
    }
    data.addAll(targets)
  }
}

private fun isTargetOutputCleared(context: CompileContext, target: BuildTarget<*>?): Boolean {
  synchronized(TARGET_WITH_CLEARED_OUTPUT) {
    val data = context.getUserData(TARGET_WITH_CLEARED_OUTPUT)
    return data != null && data.contains(target)
  }
}

private fun isDirEmpty(outputRoot: Path): Boolean {
  try {
    Files.newDirectoryStream(outputRoot).use { dirStream -> return !dirStream.iterator().hasNext() }
  }
  catch (_: IOException) {
    return true
  }
  return true
}

private fun doClearOutputFiles(context: CompileContext, target: BuildTarget<*>) {
  try {
    clearOutputFiles(context, target)
  }
  catch (e: Throwable) {
    LOG.info(e)
    val reason = e.message ?: e.javaClass.getName()
    context.processMessage(CompilerMessage("", BuildMessage.Kind.WARNING, "Problems clearing output files for target \"${target.presentableName}\": $reason"))
  }
}

private fun wrapWithModuleInfoAppender(context: CompileContext, moduleTargets: MutableCollection<ModuleBuildTarget>): CompileContext {
  val messageHandlerInterface = MessageHandler::class.java
  return ReflectionUtil.proxy(context.javaClass.getClassLoader(), CompileContext::class.java, object : InvocationHandler {
    override fun invoke(proxy: Any?, method: Method, args: Array<Any?>?): Any? {
      if (args != null && args.isNotEmpty() && messageHandlerInterface == method.declaringClass) {
        for (arg in args) {
          if (arg is CompilerMessage) {
            val compilerMessage = arg
            for (target in moduleTargets) {
              compilerMessage.addModuleName(target.module.name)
            }
            break
          }
        }
      }
      val mh = lookup.unreflect(method)
      return if (args == null) mh.invoke(context) else mh.bindTo(context).asSpreader(Array<Any>::class.java, args.size).invoke(args)
    }
  })
}

/**
 * if an output file is generated from multiple sources, make sure all of them are added for recompilation
 */
@Suppress("SpellCheckingInspection")
private fun <T : BuildTarget<R>, R : BuildRootDescriptor> completeRecompiledSourcesSet(context: CompileContext, targets: Collection<T>) {
  val scope = context.scope
  for (target in targets) {
    if (scope.isBuildForced(target)) {
      // assuming build is either forced for all targets in a chunk or for none of them
      return
    }
  }

  val projectDescriptor = context.projectDescriptor
  val affectedOutputs = CollectionFactory.createFilePathSet()
  val affectedSources = CollectionFactory.createFilePathSet()

  val mappings = ArrayList<SourceToOutputMapping>()
  for (target in targets) {
    projectDescriptor.fsState.processFilesToRecompile(context, target, object : FileProcessor<R, T> {
      private var srcToOut: SourceToOutputMapping? = null

      override fun apply(target: T, file: File, root: R): Boolean {
        val src = file.invariantSeparatorsPath
        if (!affectedSources.add(src)) {
          return true
        }

        // lazy init
        var srcToOut = this.srcToOut
        if (srcToOut == null) {
          srcToOut = projectDescriptor.dataManager.getSourceToOutputMap(target)
          mappings.add(srcToOut)
          this.srcToOut = srcToOut
        }

        val outs = srcToOut.getOutputs(src) ?: return true
        // Temporary hack for KTIJ-197
        // Change of only one input of *.kotlin_module files didn't trigger recompilation of all inputs in old behavior.
        // Now it does. It isn't yet obvious whether it is right or wrong behavior. Let's leave old behavior for a
        // while for safety and keeping kotlin incremental JPS tests green
        outs.filterTo(affectedOutputs) { "kotlin_module" != it.substringAfterLast('.') }
        return true
      }
    })
  }

  if (affectedOutputs.isEmpty()) {
    return
  }

  for (srcToOut in mappings) {
    val cursor = srcToOut.cursor()
    while (cursor.hasNext()) {
      val src = cursor.next()
      if (!affectedSources.contains(src)) {
        for (out in cursor.outputPaths) {
          if (affectedOutputs.contains(out)) {
            FSOperations.markDirtyIfNotDeleted(context, CompilationRound.CURRENT, File(src))
            break
          }
        }
      }
    }
  }
}

private fun <T : BuildRootDescriptor?> cleanOldOutputs(context: CompileContext, target: BuildTarget<T>) {
  if (!context.scope.isBuildForced(target)) {
    BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, object : DirtyFilesHolderBase<T, BuildTarget<T>>(context) {
      override fun processDirtyFiles(processor: FileProcessor<T, BuildTarget<T>>) {
        context.projectDescriptor.fsState.processFilesToRecompile(context, target, processor)
      }
    })
  }
}

private fun clearOutputFiles(
  context: CompileContext,
  mapping: SourceToOutputMapping,
  targetType: BuildTargetType<*>,
  targetId: Int,
) {
  val dirsToDelete = if (targetType is ModuleBasedBuildTargetType<*>) FileCollectionFactory.createCanonicalFileSet() else null
  val outputToTargetRegistry = context.projectDescriptor.dataManager.outputToTargetMapping
  val cursor = mapping.cursor()
  while (cursor.hasNext()) {
    cursor.next()
    val outs = cursor.outputPaths
    if (outs.isNotEmpty()) {
      val deletedPaths = ArrayList<String>()
      for (out in outs) {
        BuildOperations.deleteRecursively(out, deletedPaths, dirsToDelete)
      }
      outputToTargetRegistry.removeMappings(outs.asList(), targetId, mapping)
      if (!deletedPaths.isEmpty()) {
        context.processMessage(FileDeletedEvent(deletedPaths))
      }
    }
  }
  if (dirsToDelete != null) {
    FSOperations.pruneEmptyDirs(context, dirsToDelete)
  }
}

private fun notifyChunkRebuildRequested(context: CompileContext, chunk: ModuleChunk, builder: ModuleLevelBuilder) {
  var infoMessage = "Builder \"${builder.presentableName}\" requested rebuild of module chunk \"${chunk.name}\""
  var kind = BuildMessage.Kind.JPS_INFO
  val scope = context.scope
  for (target in chunk.targets) {
    if (!scope.isWholeTargetAffected(target)) {
      infoMessage += ".\n"
      infoMessage += "Consider building whole project or rebuilding the module."
      kind = BuildMessage.Kind.INFO
      break
    }
  }
  context.processMessage(CompilerMessage("", kind, infoMessage))
}

private fun saveInstrumentedClasses(outputConsumer: ChunkBuildOutputConsumerImpl) {
  for (compiledClass in outputConsumer.compiledClasses.values) {
    if (compiledClass.isDirty) {
      compiledClass.save()
    }
  }
}