@file:Suppress("HardCodedStringLiteral", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.kotlin

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.jps.impl.BazelKotlinFsOperationsHelper
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.FileProcessor
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.FSOperations
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.kotlin.build.GeneratedFile
import org.jetbrains.kotlin.build.GeneratedJvmClass
import org.jetbrains.kotlin.build.report.ICReporter
import org.jetbrains.kotlin.build.report.ICReporter.ReportSeverity
import org.jetbrains.kotlin.build.report.ICReporterBase
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler.K2JVMCompilerPerformanceManager
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmCliPipeline
import org.jetbrains.kotlin.compilerRunner.CompilerRunnerUtil
import org.jetbrains.kotlin.compilerRunner.DummyKotlinPaths
import org.jetbrains.kotlin.compilerRunner.JpsCompilerEnvironment
import org.jetbrains.kotlin.compilerRunner.OutputItemsCollector
import org.jetbrains.kotlin.compilerRunner.OutputItemsCollectorImpl
import org.jetbrains.kotlin.compilerRunner.ProgressReporterImpl
import org.jetbrains.kotlin.compilerRunner.toGeneratedFile
import org.jetbrains.kotlin.compilerRunner.withProgressReporter
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.ChangesCollector
import org.jetbrains.kotlin.incremental.EnumWhenTrackerImpl
import org.jetbrains.kotlin.incremental.ExpectActualTrackerImpl
import org.jetbrains.kotlin.incremental.ImportTrackerImpl
import org.jetbrains.kotlin.incremental.IncrementalCacheCommon
import org.jetbrains.kotlin.incremental.IncrementalCompilationComponentsImpl
import org.jetbrains.kotlin.incremental.IncrementalJvmCache
import org.jetbrains.kotlin.incremental.InlineConstTrackerImpl
import org.jetbrains.kotlin.incremental.LookupTrackerImpl
import org.jetbrains.kotlin.incremental.components.EnumWhenTracker
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.ImportTracker
import org.jetbrains.kotlin.incremental.components.InlineConstTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.incremental.getChangedAndImpactedSymbols
import org.jetbrains.kotlin.incremental.mapClassesFqNamesToFiles
import org.jetbrains.kotlin.incremental.mapLookupSymbolsToFiles
import org.jetbrains.kotlin.jps.build.KotlinChunk
import org.jetbrains.kotlin.jps.build.KotlinCompileContext
import org.jetbrains.kotlin.jps.build.KotlinDirtySourceFilesHolder
import org.jetbrains.kotlin.jps.build.kotlin
import org.jetbrains.kotlin.jps.build.kotlinCompileContextKey
import org.jetbrains.kotlin.jps.build.testingContext
import org.jetbrains.kotlin.jps.incremental.CacheStatus
import org.jetbrains.kotlin.jps.incremental.JpsIncrementalCache
import org.jetbrains.kotlin.jps.incremental.JpsLookupStorageManager
import org.jetbrains.kotlin.jps.model.kotlinFacet
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
import kotlin.coroutines.cancellation.CancellationException

private val classesToLoadByParent = ClassCondition { className ->
  throw IllegalStateException("Should never be called")
}

internal class BazelKotlinBuilder(
  private val isKotlinBuilderInDumbMode: Boolean,
  private val enableLookupStorageFillingInDumbMode: Boolean = false,
  private val dataManager: BazelBuildDataProvider,
  private val span: Span,
) : ModuleLevelBuilder(BuilderCategory.SOURCE_PROCESSOR) {
  override fun getPresentableName() = "Kotlin Builder"

  override fun getCompilableFileExtensions() = arrayListOf("kt")

  private val helper = KotlinContextHelper()

  override fun buildFinished(context: CompileContext) {
    ensureKotlinContextDisposed(context)
  }

  private fun ensureKotlinContextDisposed(context: CompileContext) {
    if (context.getUserData(kotlinCompileContextKey) != null) {
      synchronized(kotlinCompileContextKey) {
        val kotlinCompileContext = context.getUserData(kotlinCompileContextKey)
        if (kotlinCompileContext != null) {
          kotlinCompileContext.dispose()
          context.putUserData(kotlinCompileContextKey, null)
        }
      }
    }
  }

  override fun chunkBuildStarted(context: CompileContext, chunk: ModuleChunk) {
    val buildLogger = context.testingContext?.buildLogger
    buildLogger?.chunkBuildStarted(context, chunk)

    val kotlinContext = helper.ensureKotlinContextInitialized(context, span)

    if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context.scope)) {
      return
    }

    if (chunk.targets.none { kotlinContext.hasKotlinMarker.get(it) == true }) {
      return
    }

    val kotlinChunk = kotlinContext.getChunk(chunk) ?: return
    if (!kotlinContext.rebuildingAllKotlin) {
      val target = kotlinChunk.targets.single()
      if (target.initialLocalCacheAttributesDiff.status == CacheStatus.INVALID) {
        span.addEvent("cache is invalid, rebuilding", Attributes.of(
          AttributeKey.stringKey("diff"), target.initialLocalCacheAttributesDiff.toString(),
        ))
        kotlinContext.markChunkForRebuildBeforeBuild(kotlinChunk)
      }

      if (!isKotlinBuilderInDumbMode && kotlinChunk.isEnabled) {
        markAdditionalFilesForInitialRound(kotlinChunk, chunk, kotlinContext)
      }
    }

    buildLogger?.afterChunkBuildStarted(context, chunk)
  }

  private fun markAdditionalFilesForInitialRound(
    kotlinChunk: KotlinChunk,
    chunk: ModuleChunk,
    kotlinContext: KotlinCompileContext
  ) {
    val context = kotlinContext.jpsContext
    val dirtyFilesHolder = KotlinDirtySourceFilesHolder(
      chunk = chunk,
      context = context,
      delegate = object : DirtyFilesHolderBase<JavaSourceRootDescriptor, ModuleBuildTarget>(context) {
        override fun processDirtyFiles(processor: FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>) {
          FSOperations.processFilesToRecompile(context, chunk, processor)
        }
      }
    )

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

    val fsOperations = BazelKotlinFsOperationsHelper(context, chunk, dirtyFilesHolder, span, dataManager = dataManager)
    fsOperations.markFilesForCurrentRound(affectedByRemovedClasses.dirtyFiles.asSequence() + affectedByRemovedClasses.forceRecompileTogether)
  }

  override fun build(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
    outputConsumer: OutputConsumer
  ): ExitCode {
    val kotlinTarget = context.kotlin.targetsBinding.get(chunk.representativeTarget()) ?: return ModuleLevelBuilder.ExitCode.OK
    val kotlinDirtyFilesHolder = KotlinDirtySourceFilesHolder(chunk, context, dirtyFilesHolder)
    val fsOperations = BazelKotlinFsOperationsHelper(
      context = context,
      chunk = chunk,
      dirtyFilesHolder = kotlinDirtyFilesHolder,
      dataManager = dataManager,
      span = span,
    )
    val proposedExitCode = try {
      doBuild(
        chunk = chunk,
        representativeTarget = kotlinTarget,
        context = context,
        kotlinDirtyFilesHolder = kotlinDirtyFilesHolder,
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
    kotlinDirtyFilesHolder: KotlinDirtySourceFilesHolder,
    messageCollector: MessageCollectorAdapter,
    outputConsumer: OutputConsumer,
    fsOperations: BazelKotlinFsOperationsHelper,
  ): ExitCode {
    val kotlinContext = context.kotlin
    val kotlinChunk = context.kotlin.getChunk(chunk)!!
    if (!kotlinChunk.haveSameCompiler) {
      throw RuntimeException("Cyclically dependent modules ${kotlinChunk.presentableModulesToCompilersList} should have same compiler.")
    }

    if (!kotlinChunk.isEnabled) {
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE
    }

    val targets = chunk.targets

    val isChunkRebuilding = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context.scope) ||
      targets.any { kotlinContext.rebuildAfterCacheVersionChanged.get(it) == true }

    if (!kotlinDirtyFilesHolder.hasDirtyOrRemovedFiles) {
      if (isChunkRebuilding) {
        for (target in targets) {
          kotlinContext.hasKotlinMarker.set(target, false)
        }
      }

      targets.forEach { kotlinContext.rebuildAfterCacheVersionChanged.clean(it) }
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
      dirtyFilesHolder = kotlinDirtyFilesHolder,
      fsOperations = fsOperations,
      environment = environment,
      incrementalCaches = incrementalCaches,
      messageCollector = messageCollector,
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
      JavaBuilderUtil.registerSuccessfullyCompiled(context, kotlinDirtyFilesHolder.allDirtyFiles)
    }

    val generatedFiles = environment.outputItemsCollector.outputs
      .sortedBy { it.outputFile }
      .groupBy(keySelector = { chunk.representativeTarget() }, valueTransform = { it.toGeneratedFile(MetadataVersion.INSTANCE) })

    if (!isKotlinBuilderInDumbMode) {
      markDirtyComplementaryMultifileClasses(
        generatedFiles = generatedFiles,
        kotlinContext = kotlinContext,
        incrementalCaches = incrementalCaches,
        fsOperations = fsOperations,
      )
    }

    val kotlinTargets = kotlinContext.targetsBinding
    for ((target, outputItems) in generatedFiles) {
      val kotlinTarget = kotlinTargets.get(target) ?: error("Could not find Kotlin target for JPS target $target")
      kotlinTarget.registerOutputItems(outputConsumer, outputItems)
    }

    if (targets.any { kotlinContext.hasKotlinMarker.get(it) == null }) {
      fsOperations.markChunk(excludeFiles = kotlinDirtyFilesHolder.allDirtyFiles)
    }

    for (target in targets) {
      kotlinContext.hasKotlinMarker.set(target, true)
      kotlinContext.rebuildAfterCacheVersionChanged.clean(target)
    }

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

    environment.withProgressReporter { progress ->
      progress.progress("performing incremental compilation analysis")

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

      if (!isKotlinBuilderInDumbMode || enableLookupStorageFillingInDumbMode) {
        updateLookupStorage(lookupTracker, kotlinContext.lookupStorageManager, kotlinDirtyFilesHolder)
      }

      if (!isKotlinBuilderInDumbMode && !isChunkRebuilding) {
        processChangesUsingLookups(
          collector = changeCollector,
          compiledFiles = kotlinDirtyFilesHolder.allDirtyFiles,
          lookupStorageManager = kotlinContext.lookupStorageManager,
          fsOperations = fsOperations,
          caches = incrementalCaches.values,
          reporter = BazelJpsICReporter(span),
        )
      }
    }

    return ModuleLevelBuilder.ExitCode.OK
  }

  // todo(1.2.80): got rid of ModuleChunk (replace with KotlinChunk)
  // todo(1.2.80): introduce KotlinRoundCompileContext, move dirtyFilesHolder, fsOperations, environment to it
  private fun doCompileModuleChunk(
    chunk: KotlinChunk,
    representativeTarget: KotlinModuleBuildTarget<*>,
    context: CompileContext,
    dirtyFilesHolder: KotlinDirtySourceFilesHolder,
    fsOperations: BazelKotlinFsOperationsHelper,
    environment: JpsCompilerEnvironment,
    incrementalCaches: Map<KotlinModuleBuildTarget<*>, JpsIncrementalCache>,
    messageCollector: MessageCollectorAdapter,
  ): OutputItemsCollector? {
    chunk.targets.forEach {
      it.nextRound(context)
    }

    for (target in chunk.targets) {
      val cache = incrementalCaches[target]
      val jpsTarget = target.jpsModuleBuildTarget

      val targetDirtyFiles = dirtyFilesHolder.byTarget.get(jpsTarget)
      if (cache != null && targetDirtyFiles != null) {
        val complementaryFiles = cache.getComplementaryFilesRecursive(targetDirtyFiles.dirty.keys + targetDirtyFiles.removed)
        context.testingContext?.buildLogger?.markedAsComplementaryFiles(ArrayList(complementaryFiles))
        fsOperations.markFilesForCurrentRound(jpsTarget, complementaryFiles)

        cache.markDirty(targetDirtyFiles.dirty.keys + targetDirtyFiles.removed)
      }
    }

    val target = chunk.targets.single() as KotlinJvmModuleBuildTarget

    registerFilesToCompile(dirtyFilesHolder, context)
    require(chunk.representativeTarget == representativeTarget)
    val filesSet = dirtyFilesHolder.allDirtyFiles
    val sources = target.SourcesToCompile(
      sources = dirtyFilesHolder.getDirtyFiles(target.jpsModuleBuildTarget).values,
      removedFiles = dirtyFilesHolder.getRemovedFiles(target.jpsModuleBuildTarget),
    )
    if (!sources.logFiles()) {
      span.addEvent("not compiling, because no files affected")
      return null
    }

    if (span.isRecording) {
      span.addEvent("compiling", Attributes.of(
        AttributeKey.longKey("fileCount"), filesSet.size.toLong(),
        AttributeKey.longKey("removedFileCount"), dirtyFilesHolder.allRemovedFilesFiles.size.toLong(),
      ))
    }

    val exitCode = environment.withProgressReporter { progress ->
      progress.compilationStarted()
      JvmCliPipeline(K2JVMCompilerPerformanceManager()).execute(
        arguments = representativeTarget.jpsModuleBuildTarget.module.kotlinFacet!!.settings.compilerArguments!! as K2JVMCompilerArguments,
        services = environment.services,
        originalMessageCollector = messageCollector,
      )
    }
    @Suppress("RemoveRedundantQualifierName")
    if (org.jetbrains.kotlin.cli.common.ExitCode.INTERNAL_ERROR == exitCode) {
      messageCollector.report(CompilerMessageSeverity.ERROR, "Compiler terminated with internal error")
    }
    return environment.outputItemsCollector
  }

  private fun registerFilesToCompile(
    dirtyFilesHolder: KotlinDirtySourceFilesHolder,
    context: CompileContext,
  ) {
    val allDirtyFiles = dirtyFilesHolder.allDirtyFiles
    if (span.isRecording) {
      span.addEvent("compiling files", Attributes.of(AttributeKey.stringArrayKey("allDirtyFiles"), allDirtyFiles.map { it.path }))
    }
    JavaBuilderUtil.registerFilesToCompile(context, allDirtyFiles)
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
    dirtyFilesHolder: KotlinDirtySourceFilesHolder
  ) {
    if (lookupTracker !is LookupTrackerImpl) {
      throw AssertionError("Lookup tracker is expected to be LookupTrackerImpl, got ${lookupTracker::class.java}")
    }

    lookupStorageManager.withLookupStorage { lookupStorage ->
      lookupStorage.removeLookupsFrom(dirtyFilesHolder.allDirtyFiles.asSequence() + dirtyFilesHolder.allRemovedFilesFiles.asSequence())
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
        fsOperations.markFiles(expectedAllParts.asSequence().flatMap { cache.sourcesByInternalName(it) }
          + multifileClasses.asSequence().flatMap { it.sourceFiles })
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

private fun processChangesUsingLookups(
  collector: ChangesCollector,
  compiledFiles: Set<File>,
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
  val excludeFiles = if (compiledFiles.containsAll(dirtyFiles.forceRecompileTogether)) {
    compiledFiles
  }
  else {
    compiledFiles.minus(dirtyFiles.forceRecompileTogether)
  }
  fsOperations.markInChunkOrDependents(
    files = (dirtyFiles.dirtyFiles.asSequence() + dirtyFiles.forceRecompileTogether),
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

