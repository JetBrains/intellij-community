// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "UnstableApiUsage", "ReplaceGetOrSet", "HardCodedStringLiteral", "RemoveRedundantQualifierName")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.tracing.Tracer
import com.intellij.util.containers.FileHashStrategy
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import org.jetbrains.bazel.jvm.jps.ConsoleMessageHandler
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.BuildRootDescriptor
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.FileProcessor
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl
import org.jetbrains.jps.builders.impl.BuildTargetChunk
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import org.jetbrains.jps.builders.storage.SourceToOutputMapping
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

  fun build(context: CompileContextImpl, moduleTarget: BazelModuleBuildTarget) {
    try {
      val buildSpan = Tracer.start("IncProjectBuilder.runBuild")
      runBuild(context, moduleTarget = moduleTarget)
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

  private fun runBuild(context: CompileContextImpl, moduleTarget: BazelModuleBuildTarget) {
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

    val projectDescriptor = context.projectDescriptor
    var buildProgress: BuildProgress? = null
    try {
      val sortedTargetChunks = projectDescriptor.buildTargetIndex.getSortedTargetChunks(context)
      buildProgress = BuildProgress(
        projectDescriptor.dataManager,
        projectDescriptor.buildTargetIndex,
        sortedTargetChunks,
        Predicate { context.scope.isAffected(moduleTarget) }
      )

      require(builderRegistry.beforeTasks.isEmpty())

      val checkingSourcesSpan = Tracer.start("Building targets")
      // We don't call closeSourceToOutputStorages as we only built a single target and close the database after the build.
      // (In any case, for the new storage, it only involves removing the cached map with no actual IO close operation or using MVStore API)
      val isAffectedSpan = Tracer.start("isAffected")
      val affected = context.scope.isAffected(moduleTarget)
      isAffectedSpan.complete()
      if (affected) {
        buildTargetChunk(context = context, buildProgress = buildProgress, moduleTarget = moduleTarget)
      }
      checkingSourcesSpan.complete()

      require(builderRegistry.afterTasks.isEmpty())
      sendElapsedTimeMessages(context)
    }
    finally {
      if (buildProgress != null) {
        buildProgress.updateExpectedAverageTime()
        if (isCleanBuild && !Utils.errorsDetected(context) && !context.cancelStatus.isCanceled) {
          projectDescriptor.dataManager.targetStateManager.setLastSuccessfulRebuildDuration(buildProgress.absoluteBuildTime)
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

  private fun processDeletedPaths(context: CompileContext, target: ModuleBuildTarget): Boolean {
    var doneSomething = false
    // cleanup outputs
    val targetToRemovedSources = HashMap<BuildTarget<*>, MutableCollection<String>>()

    val dirsToDelete = HashSet<Path>()
    for (target in arrayOf(target)) {
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
            val deleted = BuildOperations.deleteRecursivelyAndCollectDeleted(Path.of(output), deletedOutputPaths, dirsToDelete)
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

  // return true if changed something, false otherwise
  private fun runModuleLevelBuilders(context: CompileContext, moduleTarget: BazelModuleBuildTarget, buildProgress: BuildProgress): Boolean {
    val chunk = ModuleChunk(setOf(moduleTarget))
    for (category in BuilderCategory.entries) {
      for (builder in builderRegistry.getBuilders(category)) {
        builder.chunkBuildStarted(context, chunk)
      }
    }

    completeRecompiledSourcesSet(context, moduleTarget)

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
                processDeletedPaths(context, moduleTarget)
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

  private fun buildTargetChunk(context: CompileContext, buildProgress: BuildProgress, moduleTarget: BazelModuleBuildTarget) {
    val buildSpan = Tracer.start { "Building ${moduleTarget.presentableName}" }
    val fsState = context.projectDescriptor.fsState
    var doneSomething: Boolean
    val targets = java.util.Set.of<BuildTarget<*>>(moduleTarget)
    try {
      context.setCompilationStartStamp(targets, System.currentTimeMillis())

      sendBuildingTargetMessages(targets, BuildingTargetProgressMessage.Event.STARTED)
      Utils.ERRORS_DETECTED_KEY.set(context, false)

      ensureFsStateInitialized(context = context, target = moduleTarget)

      doneSomething = processDeletedPaths(context, moduleTarget)

      val chunk = BuildTargetChunk(targets)
      fsState.beforeChunkBuildStart(context, targets)

      val runBuildersSpan = Tracer.start { "runBuilders " + moduleTarget.presentableName }
      doneSomething = doneSomething or runModuleLevelBuilders(context, moduleTarget, buildProgress)
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
      message.append(moduleTarget.presentableName).append(": ").append(e.javaClass.getName())
      e.message?.let {
        message.append(": ").append(it)
      }
      throw ProjectBuildException(message.toString(), e)
    }
    finally {
      buildProgress.onTargetChunkFinished(targets, context)
      try {
        // restore deleted paths that were not processed by 'integrate'
        val map = Utils.REMOVED_SOURCES_KEY.get(context)
        if (map != null) {
          for (entry in map.entries) {
            for (path in entry.value) {
              fsState.registerDeleted(context, entry.key, Path.of(path), null)
            }
          }
        }
      }
      finally {
        Utils.REMOVED_SOURCES_KEY.set(context, null)
        sendBuildingTargetMessages(targets, BuildingTargetProgressMessage.Event.FINISHED)
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

/**
 * if an output file is generated from multiple sources, make sure all of them are added for recompilation
 */
@Suppress("SpellCheckingInspection")
private fun completeRecompiledSourcesSet(context: CompileContext, moduleBuildTarget: BazelModuleBuildTarget) {
  val scope = context.scope
  if (scope.isBuildForced(moduleBuildTarget)) {
    // assuming build is either forced for all targets in a chunk or for none of them
    return
  }

  val projectDescriptor = context.projectDescriptor
  val affectedOutputs = HashSet<String>()
  val affectedSources = HashSet<String>()

  val mappings = ArrayList<SourceToOutputMapping>()
  projectDescriptor.fsState.processFilesToRecompile(context, moduleBuildTarget, object : FileProcessor<JavaSourceRootDescriptor, BazelModuleBuildTarget> {
    private var srcToOut: SourceToOutputMapping? = null

    override fun apply(target: BazelModuleBuildTarget, file: File, root: JavaSourceRootDescriptor): Boolean {
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
