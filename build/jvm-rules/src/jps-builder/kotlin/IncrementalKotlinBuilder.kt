// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.kotlin

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.bazel.jvm.jps.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.jps.OutputSink
import org.jetbrains.bazel.jvm.jps.impl.*
import org.jetbrains.bazel.jvm.kotlin.configureModule
import org.jetbrains.bazel.jvm.kotlin.createJvmPipeline
import org.jetbrains.bazel.jvm.kotlin.executeJvmPipeline
import org.jetbrains.bazel.jvm.kotlin.prepareCompilerConfiguration
import org.jetbrains.bazel.jvm.linkedSet
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.FileProcessor
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaBuilderUtil.registerFilesToCompile
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.kotlin.build.GeneratedFile
import org.jetbrains.kotlin.build.GeneratedJvmClass
import org.jetbrains.kotlin.build.report.ICReporter
import org.jetbrains.kotlin.build.report.ICReporter.ReportSeverity
import org.jetbrains.kotlin.build.report.ICReporterBase
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compilerRunner.*
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.*
import org.jetbrains.kotlin.incremental.components.*
import org.jetbrains.kotlin.jps.build.*
import org.jetbrains.kotlin.jps.build.KotlinDirtySourceFilesHolder.TargetFiles
import org.jetbrains.kotlin.jps.incremental.CacheStatus
import org.jetbrains.kotlin.jps.incremental.JpsIncrementalCache
import org.jetbrains.kotlin.jps.incremental.JpsLookupStorageManager
import org.jetbrains.kotlin.jps.targets.KotlinJvmModuleBuildTarget
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCache
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCompilationComponents
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.modules.TargetId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.preloading.ClassCondition
import org.jetbrains.kotlin.progress.CompilationCanceledException
import org.jetbrains.kotlin.progress.CompilationCanceledStatus
import java.io.File
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException

private val classesToLoadByParent = ClassCondition { className ->
  throw IllegalStateException("Should never be called")
}

internal class IncrementalKotlinBuilder(
  private val dataManager: BazelBuildDataProvider,
  private val isRebuild: Boolean,
  private val span: Span,
) : BazelTargetBuilder(BuilderCategory.SOURCE_PROCESSOR) {
  override fun getPresentableName() = "Kotlin Builder"

  override fun getCompilableFileExtensions() = arrayListOf("kt")

  override fun buildFinished(context: CompileContext) {
    ensureKotlinContextDisposed(context)
  }

  override fun chunkBuildStarted(context: CompileContext, chunk: ModuleChunk) {
    val kotlinContext = ensureKotlinContextInitialized(context, span)
    if (isRebuild) {
      return
    }

    if (chunk.targets.none { kotlinContext.hasKotlinMarker.get(it) == true }) {
      return
    }

    val kotlinChunk = kotlinContext.getChunk(chunk) ?: return
    if (!kotlinContext.rebuildingAllKotlin) {
      val target = kotlinChunk.targets.single()
      if (target.initialLocalCacheAttributesDiff.status == CacheStatus.INVALID) {
        throw RebuildRequestedException(RuntimeException("cache is invalid, rebuilding (diff=${target.initialLocalCacheAttributesDiff}"))
      }

      if (kotlinChunk.isEnabled) {
        markAdditionalFilesForInitialRound(kotlinChunk, chunk, kotlinContext)
      }
    }
  }

  private fun markAdditionalFilesForInitialRound(
    kotlinChunk: KotlinChunk,
    chunk: ModuleChunk,
    kotlinContext: KotlinCompileContext
  ) {
    val context = kotlinContext.jpsContext
    val representativeTarget = kotlinContext.targetsBinding.get(chunk.representativeTarget()) ?: return

    // dependent caches are not required, since we are not going to update caches
    val incrementalCaches = kotlinChunk.loadCaches(loadDependent = false)

    val messageCollector = MessageCollectorAdapter(context, span, representativeTarget)
    val environment = createCompileEnvironment(
      context = kotlinContext.jpsContext,
      kotlinModuleBuilderTarget = representativeTarget,
      incrementalCaches = incrementalCaches,
      lookupTracker = LookupTracker.DO_NOTHING,
      exceptActualTracer = ExpectActualTracker.DoNothing,
      inlineConstTracker = InlineConstTracker.DoNothing,
      enumWhenTracker = EnumWhenTracker.DoNothing,
      importTracker = ImportTracker.DoNothing,
      chunk = chunk,
      messageCollector = messageCollector
    ) ?: return

    val removedClasses = HashSet<String>()
    val dirtyFilesHolder = KotlinDirtySourceFilesHolder(
      chunk = chunk,
      context = context,
      delegate = object : DirtyFilesHolderBase<JavaSourceRootDescriptor, ModuleBuildTarget>(context) {
        override fun processDirtyFiles(processor: FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>) {
          context.projectDescriptor.fsState.processFilesToRecompile(context, chunk.targets.single(), processor)
        }
      }
    )
    for (target in kotlinChunk.targets) {
      val cache = incrementalCaches.get(target) ?: continue
      val dirtyFiles = dirtyFilesHolder.getDirtyFiles(target.jpsModuleBuildTarget).keys
      val removedFiles = dirtyFilesHolder.getRemovedFiles(target.jpsModuleBuildTarget)

      val existingClasses = CompilerRunnerUtil.invokeClassesFqNames(environment, dirtyFiles)
      val previousClasses = cache.classesFqNamesBySources(dirtyFiles + removedFiles)
      for (jvmClassName in previousClasses) {
        val fqName = jvmClassName.asString()
        if (!existingClasses.contains(fqName)) {
          removedClasses.add(fqName)
        }
      }
    }

    val changeCollector = ChangesCollector()
    for (it in removedClasses) {
      changeCollector.collectSignature(FqName(it), areSubclassesAffected = true)
    }
    val affectedByRemovedClasses = getDirtyFiles(
      changeCollector = changeCollector,
      caches = incrementalCaches.values,
      lookupStorageManager = kotlinContext.lookupStorageManager,
      reporter = BazelJpsICReporter(span),
    )

    val fsOperations = BazelKotlinFsOperationsHelper(context = context, chunk = chunk, span = span, dataManager = dataManager)
    fsOperations.markFilesForCurrentRound(
      files = affectedByRemovedClasses.dirtyFiles.asSequence().map { it.toPath() } + affectedByRemovedClasses.forceRecompileTogether.asSequence().map { it.toPath() },
      dirtyFilesHolder = dirtyFilesHolder,
    )
  }

  override suspend fun build(
    context: CompileContext,
    module: JpsModule,
    chunk: ModuleChunk,
    target: BazelModuleBuildTarget,
    dirtyFilesHolder: BazelDirtyFileHolder,
    outputConsumer: BazelTargetBuildOutputConsumer,
    outputSink: OutputSink
  ): ModuleLevelBuilder.ExitCode {
    val kotlinTarget = context.kotlin.targetsBinding.get(chunk.representativeTarget()) ?: return ModuleLevelBuilder.ExitCode.OK
    val fsOperations = BazelKotlinFsOperationsHelper(
      context = context,
      chunk = chunk,
      dataManager = dataManager,
      span = span,
    )
    val proposedExitCode = try {
      doBuild(
        chunk = chunk,
        representativeTarget = kotlinTarget,
        context = context,
        dirtyFilesHolder = dirtyFilesHolder,
        messageCollector = MessageCollectorAdapter(context, span, kotlinTarget),
        outputConsumer = outputConsumer,
        fsOperations = fsOperations,
      )
    }
    catch (e: CompilationCanceledException) {
      // https://youtrack.jetbrains.com/issue/KTI-2139
      throw CancellationException(e)
    }

    val actualExitCode = if (proposedExitCode == ModuleLevelBuilder.ExitCode.OK && fsOperations.hasMarkedDirty) ModuleLevelBuilder.ExitCode.ADDITIONAL_PASS_REQUIRED else proposedExitCode
    context.testingContext?.buildLogger?.buildFinished(actualExitCode)
    return actualExitCode
  }

  private fun doBuild(
    chunk: ModuleChunk,
    representativeTarget: KotlinModuleBuildTarget<*>,
    context: CompileContext,
    dirtyFilesHolder: BazelDirtyFileHolder,
    messageCollector: MessageCollectorAdapter,
    outputConsumer: OutputConsumer,
    fsOperations: BazelKotlinFsOperationsHelper,
  ): ExitCode {
    val kotlinContext = context.kotlin
    val kotlinChunk = kotlinContext.getChunk(chunk)!!
    if (!kotlinChunk.isEnabled) {
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE
    }

    val target = chunk.targets.single()

    val isChunkRebuilding = isRebuild || kotlinContext.rebuildAfterCacheVersionChanged.get(target) == true

    val kotlinDirtyFilesHolder = KotlinDirtySourceFilesHolder(chunk, context, dirtyFilesHolder)
    val dirtyByTarget = kotlinDirtyFilesHolder.byTarget.get(dirtyFilesHolder.target)
    if (dirtyByTarget == null || (dirtyByTarget.removed.isEmpty() && dirtyByTarget.dirty.isEmpty())) {
      if (isChunkRebuilding) {
        kotlinContext.hasKotlinMarker.set(target, false)
      }

      kotlinContext.rebuildAfterCacheVersionChanged.clean(target)
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE
    }

    val project = context.projectDescriptor.project
    val lookupTracker = LookupTrackerImpl(project.testingContext?.lookupTracker ?: LookupTracker.DO_NOTHING)
    val exceptActualTracker = ExpectActualTrackerImpl()
    val incrementalCaches = kotlinChunk.loadCaches(loadDependent = false)
    val inlineConstTracker = InlineConstTrackerImpl()
    val enumWhenTracker = EnumWhenTrackerImpl()
    val importTracker = ImportTrackerImpl()

    val environment = createCompileEnvironment(
      context = context,
      kotlinModuleBuilderTarget = representativeTarget,
      incrementalCaches = incrementalCaches,
      lookupTracker = lookupTracker,
      exceptActualTracer = exceptActualTracker,
      inlineConstTracker = inlineConstTracker,
      enumWhenTracker = enumWhenTracker,
      importTracker = importTracker,
      chunk = chunk,
      messageCollector = messageCollector
    ) ?: return ModuleLevelBuilder.ExitCode.ABORT

    val outputItemCollector = doCompileModuleChunk(
      chunk = kotlinChunk,
      representativeTarget = representativeTarget,
      context = context,
      targetDirtyFiles = dirtyByTarget,
      fsOperations = fsOperations,
      environment = environment,
      incrementalCaches = incrementalCaches,
      messageCollector = messageCollector,
      outputConsumer = outputConsumer,
    )

    if (outputItemCollector == null) {
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE
    }

    val compilationErrors = Utils.ERRORS_DETECTED_KEY.get(context, false)
    if (compilationErrors) {
      JavaBuilderUtil.registerFilesWithErrors(context, messageCollector.filesWithErrors.map(::File))
      return ModuleLevelBuilder.ExitCode.ABORT
    }
    else {
      JavaBuilderUtil.registerSuccessfullyCompiled(context, dirtyByTarget.dirty.keys.toList())
    }

    val generatedFiles = environment.outputItemsCollector.outputs
      .sortedBy { it.outputFile }
      .groupBy(keySelector = { chunk.representativeTarget() }, valueTransform = { it.toGeneratedFile(MetadataVersion.INSTANCE) })

    markDirtyComplementaryMultifileClasses(
      generatedFiles = generatedFiles,
      kotlinContext = kotlinContext,
      incrementalCaches = incrementalCaches,
      fsOperations = fsOperations,
    )

    val kotlinTargets = kotlinContext.targetsBinding
    for ((target, outputItems) in generatedFiles) {
      val kotlinTarget = kotlinTargets.get(target) ?: error("Could not find Kotlin target for JPS target $target")
      kotlinTarget.registerOutputItems(outputConsumer, outputItems)
    }

    if (kotlinContext.hasKotlinMarker.get(target) == null) {
      fsOperations.markChunk(excludeFiles = dirtyByTarget.dirty.keys)
    }

    kotlinContext.hasKotlinMarker.set(target, true)
    kotlinContext.rebuildAfterCacheVersionChanged.clean(target)

    for (target in kotlinChunk.targets) {
      target.doAfterBuild()
    }

    representativeTarget.updateChunkMappings(
      localContext = context,
      chunk = chunk,
      dirtyFilesHolder = kotlinDirtyFilesHolder,
      outputItems = generatedFiles,
      incrementalCaches = incrementalCaches,
      environment = environment
    )

    context.checkCanceled()

    val changeCollector = ChangesCollector()
    for ((target, files) in generatedFiles) {
      val kotlinModuleBuilderTarget = kotlinContext.targetsBinding.get(target)!!
      kotlinModuleBuilderTarget.updateCaches(
        dirtyFilesHolder = kotlinDirtyFilesHolder,
        jpsIncrementalCache = incrementalCaches.get(kotlinModuleBuilderTarget)!!,
        files = files,
        changesCollector = changeCollector,
        environment = environment,
      )
    }

    updateLookupStorage(lookupTracker, kotlinContext.lookupStorageManager, dirtyByTarget)

    if (!isChunkRebuilding) {
      doProcessChangesUsingLookups(
        collector = changeCollector,
        compiledFiles = dirtyByTarget.dirty.keys.mapTo(linkedSet()) { it.toPath() },
        lookupStorageManager = kotlinContext.lookupStorageManager,
        fsOperations = fsOperations,
        caches = incrementalCaches.values,
        reporter = BazelJpsICReporter(span),
      )
    }

    return ModuleLevelBuilder.ExitCode.OK
  }

  // todo(1.2.80): got rid of ModuleChunk (replace with KotlinChunk)
  // todo(1.2.80): introduce KotlinRoundCompileContext, move dirtyFilesHolder, fsOperations, environment to it
  private fun doCompileModuleChunk(
    chunk: KotlinChunk,
    representativeTarget: KotlinModuleBuildTarget<*>,
    context: CompileContext,
    targetDirtyFiles: TargetFiles?,
    fsOperations: BazelKotlinFsOperationsHelper,
    environment: JpsCompilerEnvironment,
    incrementalCaches: Map<KotlinModuleBuildTarget<*>, JpsIncrementalCache>,
    messageCollector: MessageCollectorAdapter,
    outputConsumer: OutputConsumer,
  ): OutputItemsCollector? {
    val target = chunk.targets.single() as KotlinJvmModuleBuildTarget
    target.nextRound(context)

    val cache = incrementalCaches.get(target)
    val jpsTarget = target.jpsModuleBuildTarget

    if (cache != null && targetDirtyFiles != null) {
      val complementaryFiles = cache.getComplementaryFilesRecursive(targetDirtyFiles.dirty.keys + targetDirtyFiles.removed)
      context.testingContext?.buildLogger?.markedAsComplementaryFiles(complementaryFiles.toList())
      fsOperations.markFilesForCurrentRound(target = jpsTarget, files = complementaryFiles.map { it.toPath() }, targetDirtyFiles = targetDirtyFiles)

      cache.markDirty(targetDirtyFiles.dirty.keys + targetDirtyFiles.removed)
    }

    if (targetDirtyFiles != null) {
      val allDirtyFiles = targetDirtyFiles.dirty.keys
      if (span.isRecording) {
        span.addEvent("compiling files", Attributes.of(AttributeKey.stringArrayKey("allDirtyFiles"), allDirtyFiles.map { it.path }))
      }
      registerFilesToCompile(context, allDirtyFiles)
    }

    require(chunk.representativeTarget == representativeTarget)
    val filesSet = if (targetDirtyFiles == null) emptySet() else targetDirtyFiles.dirty.keys
    val sources = targetDirtyFiles?.dirty?.values ?: emptyList()
    val removedFiles = targetDirtyFiles?.removed ?: emptyList()
    if (sources.isEmpty() && removedFiles.isEmpty()) {
      span.addEvent("not compiling, because no files affected")
      return null
    }

    if (span.isRecording) {
      span.addEvent("compiling", Attributes.of(
        AttributeKey.stringArrayKey("files"), sources.map { it.file.path },
        AttributeKey.longKey("fileCount"), filesSet.size.toLong(),
        AttributeKey.longKey("removedFileCount"), removedFiles.size.toLong(),
      ))
    }

    val module = jpsTarget.module
    val bazelConfigurationHolder = module.container.getChild(BazelConfigurationHolder.KIND)!!
    val config = prepareCompilerConfiguration(
      args = bazelConfigurationHolder.args,
      kotlinArgs = bazelConfigurationHolder.kotlinArgs,
      baseDir = bazelConfigurationHolder.classPathRootDir,
    )
    configureModule(
      moduleName = module.name,
      config = config,
      outFileOrDirPath = "",
      args = bazelConfigurationHolder.args,
      baseDir = bazelConfigurationHolder.classPathRootDir,
      sources = sources.map { it.file.toPath() },
      classPath = bazelConfigurationHolder.classPath.asList(),
    )

    val pipeline = createJvmPipeline(config) {
      (outputConsumer as BazelTargetBuildOutputConsumer).registerKotlincOutput(context, it.asList())
    }

    val exitCode = executeJvmPipeline(pipeline, bazelConfigurationHolder.kotlinArgs, environment.services, messageCollector)
    @Suppress("RemoveRedundantQualifierName")
    if (org.jetbrains.kotlin.cli.common.ExitCode.INTERNAL_ERROR == exitCode) {
      messageCollector.report(CompilerMessageSeverity.ERROR, "Compiler terminated with internal error")
    }
    return environment.outputItemsCollector
  }

  private fun createCompileEnvironment(
    context: CompileContext,
    kotlinModuleBuilderTarget: KotlinModuleBuildTarget<*>,
    incrementalCaches: Map<KotlinModuleBuildTarget<*>, JpsIncrementalCache>,
    lookupTracker: LookupTracker,
    exceptActualTracer: ExpectActualTracker,
    inlineConstTracker: InlineConstTracker,
    enumWhenTracker: EnumWhenTracker,
    importTracker: ImportTracker,
    chunk: ModuleChunk,
    messageCollector: MessageCollectorAdapter
  ): JpsCompilerEnvironment? {
    val builder = Services.Builder()
    builder.register(LookupTracker::class.java, implementation = lookupTracker)
    builder.register(ExpectActualTracker::class.java, implementation = exceptActualTracer)
    builder.register(CompilationCanceledStatus::class.java, object : CompilationCanceledStatus {
      override fun checkCanceled() {
        if (kotlinModuleBuilderTarget.jpsGlobalContext.cancelStatus.isCanceled) {
          throw CancellationException()
        }
      }
    })
    builder.register(InlineConstTracker::class.java, implementation = inlineConstTracker)
    builder.register(EnumWhenTracker::class.java, implementation = enumWhenTracker)
    builder.register(ImportTracker::class.java, implementation = importTracker)
    builder.register(
      IncrementalCompilationComponents::class.java,
      @Suppress("UNCHECKED_CAST")
      IncrementalCompilationComponentsImpl(incrementalCaches.mapKeys { it.key.targetId } as Map<TargetId, IncrementalCache>)
    )
    return JpsCompilerEnvironment(
      kotlinPaths = DummyKotlinPaths,
      services = builder.build(),
      classesToLoadByParent = classesToLoadByParent,
      messageCollector = messageCollector,
      outputItemsCollector = OutputItemsCollectorImpl(),
      progressReporter = ProgressReporterImpl(context, chunk)
    )
  }

  private fun updateLookupStorage(
    lookupTracker: LookupTracker,
    lookupStorageManager: JpsLookupStorageManager,
    dirtyFiles: TargetFiles,
  ) {
    if (lookupTracker !is LookupTrackerImpl) {
      throw AssertionError("Lookup tracker is expected to be LookupTrackerImpl, got ${lookupTracker::class.java}")
    }

    lookupStorageManager.withLookupStorage { lookupStorage ->
      lookupStorage.removeLookupsFrom(dirtyFiles.dirty.keys.asSequence() + dirtyFiles.removed)
      lookupStorage.addAll(lookupTracker.lookups, lookupTracker.pathInterner.values)
    }
  }

  private fun markDirtyComplementaryMultifileClasses(
    generatedFiles: Map<ModuleBuildTarget, List<GeneratedFile>>,
    kotlinContext: KotlinCompileContext,
    incrementalCaches: Map<KotlinModuleBuildTarget<*>, JpsIncrementalCache>,
    fsOperations: BazelKotlinFsOperationsHelper,
  ) {
    for ((target, files) in generatedFiles) {
      val kotlinModuleBuilderTarget = kotlinContext.targetsBinding[target] ?: continue
      val cache = incrementalCaches[kotlinModuleBuilderTarget] as? IncrementalJvmCache ?: continue
      val generated = files.filterIsInstance<GeneratedJvmClass>()
      val multifileClasses = generated.filter { it.outputClass.classHeader.kind == KotlinClassHeader.Kind.MULTIFILE_CLASS }
      val expectedAllParts = multifileClasses.flatMap { cache.getAllPartsOfMultifileFacade(it.outputClass.className).orEmpty() }
      if (multifileClasses.isEmpty()) continue
      val actualParts = generated.filter { it.outputClass.classHeader.kind == KotlinClassHeader.Kind.MULTIFILE_CLASS_PART }
        .map { it.outputClass.className.toString() }
      if (!actualParts.containsAll(expectedAllParts)) {
        fsOperations.markFiles(
          expectedAllParts.asSequence().flatMap { cache.sourcesByInternalName(it) }.map { it.toPath() }
            + multifileClasses.asSequence().flatMap { it.sourceFiles }.map { it.toPath() }
        )
      }
    }
  }
}

private class BazelJpsICReporter(private val span: Span) : ICReporterBase() {
  override fun reportCompileIteration(incremental: Boolean, sourceFiles: Collection<File>, exitCode: ExitCode) {
  }

  override fun report(message: () -> String, severity: ReportSeverity) {
    span.addEvent(message())
  }
}

private fun doProcessChangesUsingLookups(
  collector: ChangesCollector,
  compiledFiles: Set<Path>,
  lookupStorageManager: JpsLookupStorageManager,
  fsOperations: BazelKotlinFsOperationsHelper,
  caches: Iterable<JpsIncrementalCache>,
  reporter: ICReporter,
) {
  val allCaches = caches.flatMap { it.thisWithDependentCaches }

  val dirtyFiles = getDirtyFiles(
    changeCollector = collector,
    caches = allCaches,
    lookupStorageManager = lookupStorageManager,
    reporter = reporter,
  )
  // if a list of inheritors of sealed class has changed it should be recompiled with all the inheritors
  // Here we have a small optimization. Do not recompile the bunch if ALL these files were recompiled during the previous round.
  val excludeFiles = if (compiledFiles.containsAll(dirtyFiles.forceRecompileTogether.map { it.toPath() })) {
    compiledFiles
  }
  else {
    compiledFiles.minus(dirtyFiles.forceRecompileTogether.map { it.toPath() })
  }
  fsOperations.markInChunkOrDependents(
    files = (dirtyFiles.dirtyFiles.asSequence().map { it.toPath() } + dirtyFiles.forceRecompileTogether.asSequence().map { it.toPath() }),
    excludeFiles = excludeFiles,
  )
}

private data class FilesToRecompile(@JvmField val dirtyFiles: Set<File>, @JvmField val forceRecompileTogether: Set<File>)

private fun getDirtyFiles(
  changeCollector: ChangesCollector,
  caches: Iterable<IncrementalCacheCommon>,
  lookupStorageManager: JpsLookupStorageManager,
  reporter: ICReporter,
): FilesToRecompile {
  val (dirtyLookupSymbols, dirtyClassFqNames, forceRecompile) = changeCollector.changes().getChangedAndImpactedSymbols(caches, reporter)
  val dirtyFilesFromLookups = lookupStorageManager.withLookupStorage {
    mapLookupSymbolsToFiles(lookupStorage = it, lookupSymbols = dirtyLookupSymbols, reporter = reporter)
  }
  return FilesToRecompile(
    dirtyFiles = dirtyFilesFromLookups + mapClassesFqNamesToFiles(caches, dirtyClassFqNames, reporter),
    forceRecompileTogether = mapClassesFqNamesToFiles(caches, forceRecompile, reporter)
  )
}

