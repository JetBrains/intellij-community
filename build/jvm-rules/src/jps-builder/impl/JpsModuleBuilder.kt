// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "UnstableApiUsage", "ReplaceGetOrSet", "HardCodedStringLiteral", "RemoveRedundantQualifierName")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.tracing.Tracer
import org.jetbrains.bazel.jvm.jps.RequestLog
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.BuildTarget
import org.jetbrains.jps.builders.FileProcessor
import org.jetbrains.jps.builders.impl.BuildOutputConsumerImpl
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import org.jetbrains.jps.incremental.fs.BuildFSState
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
import kotlin.concurrent.Volatile

private val TARGET_WITH_CLEARED_OUTPUT = GlobalContextKey.create<MutableSet<BuildTarget<*>>>("_targets_with_cleared_output_")

private const val CLASSPATH_INDEX_FILE_NAME = "classpath.index"

// CLASSPATH_INDEX_FILE_NAME cannot be used because IDEA on run creates CLASSPATH_INDEX_FILE_NAME only if some module class is loaded,
// so, not possible to distinguish case
// "classpath.index doesn't exist because deleted on module file change" vs. "classpath.index doesn't exist because was not created"
private const val UNMODIFIED_MARK_FILE_NAME = ".unmodified"

internal class JpsProjectBuilder(
  private val log: RequestLog,
  private val isCleanBuild: Boolean,
  private val dataManager: BazelBuildDataProvider,
) {
  private val elapsedTimeNanosByBuilder = ConcurrentHashMap<Builder, AtomicLong>()
  private val numberOfSourcesProcessedByBuilder = ConcurrentHashMap<Builder, AtomicInteger>()

  fun build(context: CompileContextImpl, moduleTarget: BazelModuleBuildTarget, builders: Array<ModuleLevelBuilder>): Int {
    try {
      val buildSpan = Tracer.start("IncProjectBuilder.runBuild")
      runBuild(context, moduleTarget = moduleTarget, builders = builders)
      buildSpan.complete()
    }
    catch (e: StopBuildException) {
      // some builder decided to stop the build - report optional progress message if any
      e.message?.takeIf { it.isNotEmpty() }?.let {
        log.processMessage(ProgressMessage(it))
      }
      return if (log.hasErrors()) 1 else 0
    }
    catch (e: BuildDataCorruptedException) {
      log.warn("Internal caches are corrupted or have outdated format, forcing project rebuild: $e")
      throw RebuildRequestedException(e)
    }
    catch (e: ProjectBuildException) {
      val cause = e.cause
      if (cause is IOException || cause is BuildDataCorruptedException || (cause is RuntimeException && cause.cause is IOException)) {
        log.warn("Internal caches are corrupted or have outdated format, forcing project rebuild: $e")
        throw RebuildRequestedException(cause)
      }
      else {
        // should stop the build with error
        throw e
      }
    }
    return 0
  }

  private fun runBuild(context: CompileContextImpl, moduleTarget: BazelModuleBuildTarget, builders: Array<ModuleLevelBuilder>) {
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
    val allModuleLevelBuildersBuildStartedSpan = Tracer.start("All ModuleLevelBuilder.buildStarted")
    for (builder in builders) {
      builder.buildStarted(context)
    }
    allModuleLevelBuildersBuildStartedSpan.complete()

    try {
      val checkingSourcesSpan = Tracer.start("Building targets")
      // We don't call closeSourceToOutputStorages as we only built a single target and close the database after the build.
      // (In any case, for the new storage, it only involves removing the cached map with no actual IO close operation or using MVStore API)
      val isAffectedSpan = Tracer.start("isAffected")
      val affected = context.scope.isAffected(moduleTarget)
      isAffectedSpan.complete()
      if (affected) {
        buildTargetChunk(context = context, target = moduleTarget, builders = builders)
      }
      checkingSourcesSpan.complete()

      sendElapsedTimeMessages(context)
    }
    finally {
      for (builder in builders) {
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
      .sorted(Comparator.comparing<BuilderStatisticsMessage?, String?>(Function { it.builderName }))
      .forEach { context.processMessage(it) }
  }

  private fun deleteAndCollectDeleted(file: Path, deletedPaths: MutableList<Path>, parentDirs: MutableSet<Path>): Boolean {
    val deleted = Files.deleteIfExists(file)
    if (deleted) {
      deletedPaths.add(file)
      file.parent?.let {
        parentDirs.add(it)
      }
    }
    return deleted
  }

  private fun processDeletedPaths(context: CompileContext, target: ModuleBuildTarget): Boolean {
    val dirsToDelete = HashSet<Path>()
    val deletedPaths = context.projectDescriptor.fsState.getAndClearDeletedPaths(target).asSequence().map { Path.of(it) }.sorted().toList()
    if (deletedPaths.isEmpty()) {
      return false
    }

    if (isTargetOutputCleared(context, target)) {
      return false
    }

    var doneSomething = false
    val dataManager = context.projectDescriptor.dataManager
    val sourceToOutputStorage = dataManager.getSourceToOutputMap(target)
    // actually delete outputs associated with removed paths
    for (deletedFile in deletedPaths) {
      // deleting outputs corresponding to a non-existing source
      val outputs = sourceToOutputStorage.getOutputs(deletedFile)
      if (outputs != null && !outputs.isEmpty()) {
        val deletedOutputFiles = ArrayList<Path>()
        for (output in outputs) {
          val deleted = deleteAndCollectDeleted(output, deletedOutputFiles, dirsToDelete)
          if (deleted) {
            doneSomething = true
          }
        }
        if (!deletedOutputFiles.isEmpty()) {
          log.info("Deleted files: $deletedOutputFiles")
          context.processMessage(FileDeletedEvent(deletedOutputFiles.map { it.toString() }))
        }
      }

      // check if the deleted source was associated with a form
      val sourceToFormMap = dataManager.getSourceToFormMap(target)
      val boundForms = sourceToFormMap.getOutputs(deletedFile)
      if (boundForms != null) {
        for (formFile in boundForms) {
          if (Files.exists(formFile)) {
            FSOperations.markDirty(context, CompilationRound.CURRENT, formFile.toFile())
          }
        }
        sourceToFormMap.remove(deletedFile)
      }
    }

    val existing = Utils.REMOVED_SOURCES_KEY.get(context).get(target)
    if (existing == null) {
      Utils.REMOVED_SOURCES_KEY.set(context, java.util.Map.of(target, deletedPaths as Collection<Path>))
    }
    else {
      val set = LinkedHashSet<Path>()
      set.addAll(existing)
      set.addAll(deletedPaths)
      Utils.REMOVED_SOURCES_KEY.set(context, java.util.Map.of(target, set as Collection<Path>))
    }

    FSOperations.pruneEmptyDirs(context, dirsToDelete)
    return doneSomething
  }

  // return true if changed something, false otherwise
  private fun runModuleLevelBuilders(
    context: CompileContext,
    moduleTarget: BazelModuleBuildTarget,
    builders: Array<ModuleLevelBuilder>,
  ): Boolean {
    val chunk = ModuleChunk(java.util.Set.of<ModuleBuildTarget>(moduleTarget))
    for (builder in builders) {
      builder.chunkBuildStarted(context, chunk)
    }

    if (!isCleanBuild) {
      completeRecompiledSourcesSet(context, moduleTarget)
    }

    var doneSomething = false
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

      var rebuildFromScratchRequested = false
      var nextPassRequired: Boolean
      do {
        nextPassRequired = false
        fsState.beforeNextRoundStart(context, chunk)

        if (!isCleanBuild) {
          val cleanedSources = BuildOperations.cleanOutputsCorrespondingToChangedFiles(context, dirtyFilesHolder)
          for (entry in cleanedSources.entries) {
            val files = entry.value.keys
            if (files.isEmpty()) {
              continue
            }

            val mapping = context.projectDescriptor.dataManager.getSourceToOutputMap(entry.key)
            for (sourceFile in files) {
              val outputs = entry.value.get(sourceFile)!!
              mapping.setOutputs(sourceFile, outputs)
              if (!outputs.isEmpty()) {
                log.info("Some outputs were not removed for $sourceFile source file: $outputs")
              }
            }
          }
        }

        try {
          var buildersPassed = 0
          var instrumentedClassesSaved = false
          for (builder in builders) {
            if (builder.category == BuilderCategory.CLASS_POST_PROCESSOR && !instrumentedClassesSaved) {
              instrumentedClassesSaved = true
              // ensure changes from instruments are visible to class post-processors
              saveInstrumentedClasses(outputConsumer)
            }

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
              if (!rebuildFromScratchRequested && !isCleanBuild) {
                notifyChunkRebuildRequested(context, chunk, builder)
                // allow rebuild from scratch only once per chunk
                rebuildFromScratchRequested = true
                // forcibly mark all files in the chunk dirty
                fsState.clearContextRoundData(context)
                FSOperations.markDirty(context, CompilationRound.NEXT, chunk, null)
                // reverting to the beginning
                nextPassRequired = true
                outputConsumer.clear()
                break
              }
              else {
                log.debug("Builder ${builder.presentableName} requested second chunk rebuild")
              }
            }

            buildersPassed++
          }
        }
        finally {
          outputConsumer.setCurrentBuilderName(null)
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
      for (builder in builders) {
        builder.chunkBuildFinished(context, chunk)
      }
      if (Utils.errorsDetected(context)) {
        context.processMessage(CompilerMessage("", BuildMessage.Kind.JPS_INFO, "Errors occurred while compiling module ${chunk.presentableShortName}"))
      }
    }

    return doneSomething
  }

  private fun initFsStateForCleanBuild(context: CompileContext, target: BuildTarget<*>) {
    val fsState = context.projectDescriptor.fsState
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

  private fun buildTargetChunk(context: CompileContext, target: BazelModuleBuildTarget, builders: Array<ModuleLevelBuilder>) {
    val buildSpan = Tracer.start { "Building ${target.presentableName}" }
    val fsState = context.projectDescriptor.fsState
    var doneSomething: Boolean
    val targets = java.util.Set.of<BuildTarget<*>>(target)
    try {
      context.setCompilationStartStamp(targets, System.currentTimeMillis())

      sendBuildingTargetMessages(targets, BuildingTargetProgressMessage.Event.STARTED)
      Utils.ERRORS_DETECTED_KEY.set(context, false)

      val fsState = context.projectDescriptor.fsState
      if (isCleanBuild) {
        initFsStateForCleanBuild(context = context, target = target)
      }
      else {
        require(!fsState.isInitialScanPerformed(target))
        initTargetFsStateForNonInitialBuild(context = context, target = target, log = log, dataManager = dataManager)
      }

      doneSomething = processDeletedPaths(context, target)

      fsState.beforeChunkBuildStart(context, targets)

      val runBuildersSpan = Tracer.start { "runBuilders " + target.presentableName }
      doneSomething = doneSomething or runModuleLevelBuilders(context, target, builders)
      runBuildersSpan.complete()

      fsState.clearContextRoundData(context)
      fsState.clearContextChunk(context)

      if (doneSomething) {
        BuildOperations.markTargetsUpToDate(context, targets)
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
      message.append(target.presentableName).append(": ").append(e.javaClass.getName())
      e.message?.let {
        message.append(": ").append(it)
      }
      throw ProjectBuildException(message.toString(), e)
    }
    finally {
      try {
        // restore deleted paths that were not processed by 'integrate'
        val map = Utils.REMOVED_SOURCES_KEY.get(context)
        if (map != null) {
          for (entry in map.entries) {
            for (file in entry.value) {
              fsState.registerDeleted(context, entry.key, file, null)
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
    log.processMessage(BuildingTargetProgressMessage(targets, event))
  }

  private fun storeBuilderStatistics(builder: Builder, elapsedTime: Long, processedFiles: Int) {
    elapsedTimeNanosByBuilder.computeIfAbsent(builder) { AtomicLong() }.addAndGet(elapsedTime)
    numberOfSourcesProcessedByBuilder.computeIfAbsent(builder) { AtomicInteger() }.addAndGet(processedFiles)
  }
}

private class ChunkBuildOutputConsumerImpl(private val context: CompileContext) : OutputConsumer {
  private val targetToConsumer = HashMap<BuildTarget<*>, BuildOutputConsumerImpl>()
  private val classes = HashMap<String, CompiledClass>()
  private val targetToClassesMap = HashMap<BuildTarget<*>, MutableCollection<CompiledClass>>()
  private val outputToBuilderNameMap = Collections.synchronizedMap(HashMap<File, String>())

  @Volatile
  private var currentBuilderName: String? = null

  fun setCurrentBuilderName(builderName: String?) {
    currentBuilderName = builderName
  }

  override fun getTargetCompiledClasses(target: BuildTarget<*>): Collection<CompiledClass> {
    return targetToClassesMap.get(target) ?: return java.util.List.of()
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

  override fun registerOutputFile(target: BuildTarget<*>, outputFile: File, sourcePaths: Collection<String>) {
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
    var consumer = targetToConsumer.get(target)
    if (consumer == null) {
      consumer = BuildOutputConsumerImpl(target, context)
      targetToConsumer.put(target, consumer)
    }
    consumer.registerOutputFile(outputFile, sourcePaths)
  }

  fun fireFileGeneratedEvents() {
    for (consumer in targetToConsumer.values) {
      consumer.fireFileGeneratedEvent()
    }
  }

  fun getNumberOfProcessedSources(): Int {
    var total = 0
    for (consumer in targetToConsumer.values) {
      total += consumer.numberOfProcessedSources
    }
    return total
  }

  fun clear() {
    targetToConsumer.clear()
    classes.clear()
    targetToClassesMap.clear()
    outputToBuilderNameMap.clear()
  }
}

private class ChainedTargetsBuildListener(private val context: CompileContextImpl) : BuildListener {
  override fun filesGenerated(event: FileGeneratedEvent) {
    val projectDescriptor = context.projectDescriptor
    val fsState = projectDescriptor.fsState
    val buildRootIndex = projectDescriptor.buildRootIndex as BazelBuildRootIndex
    for (pair in event.paths) {
      val relativePath = pair.getSecond()
      val file = if (relativePath == ".") Path.of(pair.getFirst()) else Path.of(pair.getFirst(), relativePath)
      val buildRootDescriptor = buildRootIndex.fileToDescriptors.get(file) ?: continue
      val target = buildRootDescriptor.target
      if (event.sourceTarget != target) {
        fsState.markDirty(
          /* context = */ context,
          /* round = */ CompilationRound.NEXT,
          /* file = */ file,
          /* buildRootDescriptor = */ buildRootDescriptor,
          /* stampStorage = */ projectDescriptor.dataManager.getFileStampStorage(target),
          /* saveEventStamp = */ false,
        )
      }
    }
  }

  override fun filesDeleted(event: FileDeletedEvent) {
    val fsState = context.projectDescriptor.fsState
    val buildRootIndex = context.projectDescriptor.buildRootIndex as BazelBuildRootIndex
    for (path in event.filePaths) {
      val file = Path.of(path)
      val rootDescriptor = buildRootIndex.fileToDescriptors.get(file) ?: continue
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


private inline fun processFilesToRecompile(
  context: CompileContext,
  target: BazelModuleBuildTarget,
  fsState: BuildFSState,
  processor: (Path, JavaSourceRootDescriptor) -> Boolean,
): Boolean {
  val scope = context.scope
  val delta = fsState.getEffectiveFilesDelta(context, target)
  delta.lockData()
  try {
    for (entry in delta.sourceMapToRecompile.entries) {
      val root = entry.key as JavaSourceRootDescriptor
      for (file in entry.value) {
        if (!scope.isAffected(target, file)) {
          continue
        }
        if (!processor(file, root)) {
          return false
        }
      }
    }
    return true
  }
  finally {
    delta.unlockData()
  }
}

/**
 * if an output file is generated from multiple sources, make sure all of them are added for recompilation
 */
private fun completeRecompiledSourcesSet(context: CompileContext, target: BazelModuleBuildTarget) {
  val projectDescriptor = context.projectDescriptor
  val affected = mutableListOf<List<String>>()
  val affectedSources = HashSet<Path>()

  val sourceToOut = projectDescriptor.dataManager.getSourceToOutputMap(target) as BazelSourceToOutputMapping
  processFilesToRecompile(context = context, target = target, fsState = projectDescriptor.fsState) { file, root ->
    if (!affectedSources.add(file)) {
      sourceToOut.getDescriptor(file)?.outputs?.let {
        affected.add(it)
      }
    }
    true
  }

  if (affected.isEmpty()) {
    return
  }

  // one output can be produced by different sources, so, we find intersection by outputs
  val affectedSourceFiles = sourceToOut.findAffectedSources(affected)
  if (affectedSourceFiles.isEmpty()) {
    return
  }

  val fileToDescriptors = (context.projectDescriptor.buildRootIndex as BazelBuildRootIndex).fileToDescriptors
  val stampStorage = projectDescriptor.dataManager.getFileStampStorage(target)
  for (file in affectedSourceFiles) {
    val rootDescriptor = fileToDescriptors.get(file) ?: continue
    context.projectDescriptor.fsState.markDirtyIfNotDeleted(
      context,
      CompilationRound.CURRENT,
      file,
      rootDescriptor,
      stampStorage
    )
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
