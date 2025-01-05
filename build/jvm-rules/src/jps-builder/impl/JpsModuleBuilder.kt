// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "UnstableApiUsage", "ReplaceGetOrSet", "HardCodedStringLiteral")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.openapi.util.text.Formats
import com.intellij.tracing.Tracer
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.FileHashStrategy
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import org.jetbrains.bazel.jvm.jps.ConsoleMessageHandler
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.FileProcessor
import org.jetbrains.jps.builders.ModuleBasedTarget
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
import java.io.File
import java.io.IOException
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function
import java.util.function.Predicate
import kotlin.concurrent.Volatile

private val TARGET_WITH_CLEARED_OUTPUT = GlobalContextKey.create<MutableSet<BuildTarget<*>>>("_targets_with_cleared_output_")

private const val CLASSPATH_INDEX_FILE_NAME = "classpath.index"

// CLASSPATH_INDEX_FILE_NAME cannot be used because IDEA on run creates CLASSPATH_INDEX_FILE_NAME only if some module class is loaded,
// so, not possible to distinguish case
// "classpath.index doesn't exist because deleted on module file change" vs. "classpath.index doesn't exist because was not created"
private const val UNMODIFIED_MARK_FILE_NAME = ".unmodified"

internal class JpsProjectBuilder(
  private val builderRegistry: BuilderRegistry,
  private val messageHandler: ConsoleMessageHandler,
  private val isCleanBuild: Boolean,
) {
  private val totalModuleLevelBuilderCount = builderRegistry.moduleLevelBuilderCount
  private val elapsedTimeNanosByBuilder = ConcurrentHashMap<Builder, AtomicLong>()
  private val numberOfSourcesProcessedByBuilder = ConcurrentHashMap<Builder, AtomicInteger>()

  suspend fun build(context: CompileContextImpl) {
    try {
      val buildSpan = Tracer.start("IncProjectBuilder.runBuild")
      runBuild(context)
      buildSpan.complete()
    }
    catch (e: StopBuildException) {
      // some builder decided to stop the build - report optional progress message if any
      e.message?.takeIf { it.isNotEmpty() }?.let {
        messageHandler.processMessage(ProgressMessage(it))
      }
    }
    catch (e: BuildDataCorruptedException) {
      messageHandler.warn("Internal caches are corrupted or have outdated format, forcing project rebuild: $e")
      throw RebuildRequestedException(e)
    }
    catch (e: ProjectBuildException) {
      val cause = e.cause
      if (cause is IOException || cause is BuildDataCorruptedException || (cause is RuntimeException && cause.cause is IOException)) {
        messageHandler.warn("Internal caches are corrupted or have outdated format, forcing project rebuild: $e")
        throw RebuildRequestedException(cause)
      }
      else {
        // should stop the build with error
        throw e
      }
    }
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
    require(builderRegistry.targetBuilders.isEmpty())
    val allModuleLevelBuildersBuildStartedSpan = Tracer.start("All ModuleLevelBuilder.buildStarted")
    for (builder in builderRegistry.moduleLevelBuilders) {
      builder.buildStarted(context)
    }
    allModuleLevelBuildersBuildStartedSpan.complete()

    var buildProgress: BuildProgress? = null
    try {
      val projectDescriptor = context.projectDescriptor
      val sortedTargetChunks = projectDescriptor.buildTargetIndex.getSortedTargetChunks(context)
      buildProgress = BuildProgress(
        projectDescriptor.dataManager,
        projectDescriptor.buildTargetIndex,
        sortedTargetChunks,
        Predicate { isBuildChunkAffected(scope = context.scope, chunk = it) }
      )

      require(builderRegistry.beforeTasks.isEmpty())

      val checkingSourcesSpan = Tracer.start("Building targets")
      BuildTaskLauncher(context = context, buildProgress = buildProgress, builder = this).buildInParallel()
      checkingSourcesSpan.complete()

      require(builderRegistry.afterTasks.isEmpty())
      sendElapsedTimeMessages(context)
    }
    finally {
      if (buildProgress != null) {
        buildProgress.updateExpectedAverageTime()
        if (isCleanBuild && !Utils.errorsDetected(context) && !context.cancelStatus.isCanceled) {
          context.projectDescriptor.dataManager.targetStateManager.setLastSuccessfulRebuildDuration(buildProgress.absoluteBuildTime)
        }
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

      return runModuleLevelBuilders(ModuleInfoAwareCompileContextCopy(context, moduleTargets), ModuleChunk(moduleTargets), buildProgress)
    }

    val target = targets.iterator().next()
    if (target is ModuleBuildTarget) {
      @Suppress("RemoveRedundantQualifierName")
      val mbt = java.util.Set.of(target)
      return runModuleLevelBuilders(ModuleInfoAwareCompileContextCopy(context, mbt), ModuleChunk(mbt), buildProgress)
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

  private fun processDeletedPaths(context: CompileContext, targets: Set<BuildTarget<*>>): Boolean {
    var doneSomething = false
    // cleanup outputs
    val targetToRemovedSources = HashMap<BuildTarget<*>, MutableCollection<String>>()

    val dirsToDelete = HashSet<Path>()
    for (target in targets) {
      val deletedPaths = context.projectDescriptor.fsState.getAndClearDeletedPaths(target)
      if (deletedPaths.isEmpty()) {
        continue
      }

      targetToRemovedSources.put(target, deletedPaths)
      if (isTargetOutputCleared(context, target)) {
        continue
      }

      val dataManager = context.projectDescriptor.dataManager
      val buildTargetId = dataManager.targetStateManager.getBuildTargetId(target)
      val shouldPruneEmptyDirs = target is ModuleBasedTarget<*>
      val sourceToOutputStorage = dataManager.getSourceToOutputMap(target)
      val logger = context.loggingManager.projectBuilderLogger
      // actually delete outputs associated with removed paths
      for (deletedSource in deletedPaths.sorted()) {
        // deleting outputs corresponding to a non-existing source
        val outputs = sourceToOutputStorage.getOutputs(deletedSource)
        if (outputs != null && !outputs.isEmpty()) {
          val deletedOutputPaths = ArrayList<String>()
          val outputToSourceRegistry = dataManager.outputToTargetMapping
          for (output in outputToSourceRegistry.removeTargetAndGetSafeToDeleteOutputs(outputs, buildTargetId, sourceToOutputStorage)) {
            val deleted = BuildOperations.deleteRecursivelyAndCollectDeleted(Path.of(output), deletedOutputPaths, if (shouldPruneEmptyDirs) dirsToDelete else null)
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
              val formFile = Path.of(formPath)
              if (Files.exists(formFile)) {
                FSOperations.markDirty(context, CompilationRound.CURRENT, formFile.toFile())
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
          if (paths == null) {
            targetToRemovedSources.put(entry.key, entry.value)
          }
          else {
            paths.addAll(entry.value)
          }
        }
      }
      Utils.REMOVED_SOURCES_KEY.set(context, targetToRemovedSources)
    }

    FSOperations.pruneEmptyDirs(context, dirsToDelete)
    return doneSomething
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
      val fsState = context.projectDescriptor.fsState
      val dirtyFilesHolder = object : DirtyFilesHolderBase<JavaSourceRootDescriptor, ModuleBuildTarget>(context) {
        override fun processDirtyFiles(processor: FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>) {
          for (target in chunk.targets) {
            fsState.processFilesToRecompile(context, target, processor)
          }
        }
      }

      do {
        nextPassRequired = false
        fsState.beforeNextRoundStart(context, chunk)

        if (!JavaBuilderUtil.isForcedRecompilationAllJavaModules(context.scope)) {
          val cleanedSources = BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder)
          for (entry in cleanedSources.entries) {
            val files = entry.value.keys
            if (files.isEmpty()) {
              continue
            }

            val mapping = context.projectDescriptor.dataManager.getSourceToOutputMap(entry.key)
            for (srcFile in files) {
              val outputs = entry.value.get(srcFile)!!
              mapping.setOutputs(srcFile.path, outputs)
              if (!outputs.isEmpty()) {
                messageHandler.info("Some outputs were not removed for ${srcFile.path} source file: $outputs")
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
                storeBuilderStatistics(
                  builder = builder,
                  elapsedTime = System.nanoTime() - start,
                  processedFiles = outputConsumer.getNumberOfProcessedSources() - processedSourcesBefore,
                )

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
                      fsState.clearContextRoundData(context)
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
                    messageHandler.debug("Builder ${builder.presentableName} requested second chunk rebuild")
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

  private fun ensureFsStateInitialized(context: CompileContext, target: BuildTarget<*>) {
    val fsState = context.projectDescriptor.fsState
    if (isCleanBuild) {
      val targetRoots = (context.projectDescriptor.buildRootIndex as BazelBuildRootIndex).descriptors
      fsState.getDelta(target).clearRecompile(targetRoots)
      for (rootDescriptor in targetRoots) {
        // if it is a full project rebuild, all storages are already completely cleared;
        // so passing null as stampStorage because there is no need to access the storage to clear non-existing data
        fsState.markDirty(
          /* context = */ context,
          /* round = */ CompilationRound.CURRENT,
          /* file = */ rootDescriptor.file,
          /* buildRootDescriptor = */ rootDescriptor,
          /* stampStorage = */ null,
          /* saveEventStamp = */ false,
        )
      }
      FSOperations.addCompletelyMarkedDirtyTarget(context, target)
      fsState.markInitialScanPerformed(target)
    }
    else if (!fsState.isInitialScanPerformed(target)) {
      BuildOperations.initTargetFSState(context, target, false)
    }
  }

  internal fun buildTargetChunk(context: CompileContext, chunk: BuildTargetChunk, buildProgress: BuildProgress) {
    val buildSpan = Tracer.start { "Building ${chunk.presentableName}" }
    val fsState = context.projectDescriptor.fsState
    var doneSomething: Boolean
    try {
      context.setCompilationStartStamp(chunk.targets, System.currentTimeMillis())

      sendBuildingTargetMessages(chunk.targets, BuildingTargetProgressMessage.Event.STARTED)
      Utils.ERRORS_DETECTED_KEY.set(context, false)

      for (target in chunk.targets) {
        ensureFsStateInitialized(context = context, target = target)
      }

      doneSomething = processDeletedPaths(context, chunk.targets)

      fsState.beforeChunkBuildStart(context, chunk)

      val runBuildersSpan = Tracer.start { "runBuilders " + chunk.presentableName }
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
      val message = StringBuilder()
      message.append(chunk.presentableName).append(": ").append(e.javaClass.getName())
      e.message?.let {
        message.append(": ").append(it)
      }
      throw ProjectBuildException(message.toString(), e)
    }
    finally {
      buildProgress.onTargetChunkFinished(chunk, context)
      try {
        // restore deleted paths that were not processed by 'integrate'
        val map = Utils.REMOVED_SOURCES_KEY.get(context)
        if (map != null) {
          for (entry in map.entries) {
            val paths = entry.value
            if (paths != null) {
              for (path in paths) {
                fsState.registerDeleted(context, entry.key, Path.of(path), null)
              }
            }
          }
        }
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
    val projectDescriptor = context.projectDescriptor
    val fsState = projectDescriptor.fsState
    for (pair in event.paths) {
      val relativePath = pair.getSecond()
      val file = if (relativePath == ".") File(pair.getFirst()) else File(pair.getFirst(), relativePath)
      for (buildRootDescriptor in projectDescriptor.buildRootIndex.findAllParentDescriptors<BuildRootDescriptor>(file, context)) {
        val target = buildRootDescriptor.target
        if (event.sourceTarget != target) {
          fsState.markDirty(context, file, buildRootDescriptor, projectDescriptor.dataManager.getFileStampStorage(target), false)
        }
      }
    }
  }

  override fun filesDeleted(event: FileDeletedEvent) {
    val fsState = context.projectDescriptor.fsState
    val rootIndex = context.projectDescriptor.buildRootIndex as BazelBuildRootIndex
    for (path in event.filePaths) {
      val file = Path.of(path)
      val rootDescriptor = rootIndex.fileToDescriptors.get(file) ?: continue
      fsState.registerDeleted(context, rootDescriptor.target, file)
    }
  }
}

private fun calculateEstimatedBuildTime(
  projectDescriptor: ProjectDescriptor,
  target: ModuleBuildTarget,
  messageHandler: ConsoleMessageHandler,
): Long {
  // compute estimated times for dirty targets
  var affectedTargets = 0
  val avgTimeToBuild = projectDescriptor.dataManager.targetStateManager.getAverageBuildTime(target.targetType)
  val estimatedBuildTime = if (avgTimeToBuild > 0) {
    affectedTargets = 1
    // 1. in general case, this time should include dependency analysis and cache update times
    // 2. need to check isAffected() since some targets (like artifacts) may be unaffected even for rebuild
    avgTimeToBuild
  }
  else {
    0L
  }
  messageHandler.info("Affected build targets count: $affectedTargets")
  return estimatedBuildTime
}

internal fun reportRebuiltModules(context: CompileContextImpl) {
  val modules = BuildTargetConfiguration.MODULES_WITH_TARGET_CONFIG_CHANGED_KEY.get(context)
  if (modules.isNullOrEmpty()) {
    return
  }

  val text = "${modules.joinToString { m -> "'" + m.name + "'" }} was fully rebuilt due to project configuration changes"
  context.processMessage(CompilerMessage("", BuildMessage.Kind.INFO, text))
}

internal fun reportUnprocessedChanges(context: CompileContextImpl, moduleTarget: ModuleBuildTarget) {
  if (context.projectDescriptor.fsState.hasUnprocessedChanges(context, moduleTarget)) {
    context.processMessage(UnprocessedFSChangesNotification())
  }
}

private fun isTargetOutputCleared(context: CompileContext, target: BuildTarget<*>?): Boolean {
  synchronized(TARGET_WITH_CLEARED_OUTPUT) {
    val data = context.getUserData(TARGET_WITH_CLEARED_OUTPUT)
    return data != null && data.contains(target)
  }
}

private class ModuleInfoAwareCompileContextCopy(
  private val context: CompileContext,
  private val moduleTargets: Collection<ModuleBuildTarget>,
) : CompileContext by context {
  override fun processMessage(message: BuildMessage) {
    if (message is CompilerMessage) {
      for (target in moduleTargets) {
        message.addModuleName(target.module.name)
      }
    }
    context.processMessage(message)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun isProjectRebuild() = false

  override fun isCanceled(): Boolean = context.isCanceled
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
            FSOperations.markDirtyIfNotDeleted(context, CompilationRound.CURRENT, Path.of(src))
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

internal fun checkRebuildRequired(
  scope: CompileScope,
  projectDescriptor: ProjectDescriptor,
  moduleTarget: ModuleBuildTarget,
  isDebugEnabled: Boolean,
  messageHandler: ConsoleMessageHandler,
): Boolean {
  val targetStateManager = projectDescriptor.dataManager.targetStateManager
  val timeThreshold = targetStateManager.getLastSuccessfulRebuildDuration() * 95 / 100 // 95% of last registered clean rebuild time
  if (timeThreshold <= 0) {
    if (isDebugEnabled) {
      messageHandler.debug("Rebuild heuristic: no stats available")
    }
    return false
  }

  // check that this is a whole-project incremental build
  // checking only JavaModuleBuildTargetType because these target types directly correspond to project modules
  val type = JavaModuleBuildTargetType.PRODUCTION
  if (!scope.isAllTargetsOfTypeAffected(type)) {
    if (isDebugEnabled) {
      messageHandler.debug("Rebuild heuristic: skipping the check because some targets are excluded from compilation scope," +
        " e.g. targets of type ${type.typeId}")
    }
    return false
  }

  // compute estimated times for dirty targets
  val estimatedWorkTime = calculateEstimatedBuildTime(projectDescriptor, moduleTarget, messageHandler)
  if (isDebugEnabled) {
    messageHandler.debug("Rebuild heuristic: estimated build time / timeThreshold : $estimatedWorkTime / $timeThreshold")
  }

  if (estimatedWorkTime < timeThreshold) {
    return false
  }

  val message = """
    Too many files require recompilation, forcing full rebuild.
     * Estimated build duration (linear): ${Formats.formatDuration(estimatedWorkTime)}
     * Last successful rebuild duration (linear): ${Formats.formatDuration(targetStateManager.getLastSuccessfulRebuildDuration())}
     * Rebuild heuristic time threshold: ${Formats.formatDuration(timeThreshold)}
  """.trimIndent()
  messageHandler.info(message)
  return true
}

