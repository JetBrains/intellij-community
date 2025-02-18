// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet", "PackageDirectoryMismatch")

package org.jetbrains.bazel.jvm.jps.kotlin

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.ensureActive
import org.jetbrains.bazel.jvm.concat
import org.jetbrains.bazel.jvm.emptyList
import org.jetbrains.bazel.jvm.emptySet
import org.jetbrains.bazel.jvm.hashSet
import org.jetbrains.bazel.jvm.jps.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.jps.OutputSink
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileContext
import org.jetbrains.bazel.jvm.jps.impl.BazelDirtyFileHolder
import org.jetbrains.bazel.jvm.jps.impl.BazelKotlinFsOperationsHelper
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.BazelTargetBuildOutputConsumer
import org.jetbrains.bazel.jvm.jps.impl.BazelTargetBuilder
import org.jetbrains.bazel.jvm.jps.impl.markFilesForCurrentRound
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
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.build.GeneratedFile
import org.jetbrains.kotlin.build.GeneratedJvmClass
import org.jetbrains.kotlin.build.isModuleMappingFile
import org.jetbrains.kotlin.build.report.ICReporter
import org.jetbrains.kotlin.build.report.ICReporter.ReportSeverity
import org.jetbrains.kotlin.build.report.ICReporterBase
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.compilerRunner.DummyKotlinPaths
import org.jetbrains.kotlin.compilerRunner.JpsCompilerEnvironment
import org.jetbrains.kotlin.compilerRunner.OutputItemsCollectorImpl
import org.jetbrains.kotlin.compilerRunner.ProgressReporterImpl
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
import org.jetbrains.kotlin.incremental.parsing.classesFqNames
import org.jetbrains.kotlin.jps.build.KotlinChunk
import org.jetbrains.kotlin.jps.build.KotlinCompileContext
import org.jetbrains.kotlin.jps.build.KotlinDirtySourceFilesHolder
import org.jetbrains.kotlin.jps.build.KotlinDirtySourceFilesHolder.TargetFiles
import org.jetbrains.kotlin.jps.build.testingContext
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
import org.jetbrains.kotlin.progress.CompilationCanceledStatus
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

private val classesToLoadByParent = ClassCondition { className ->
  throw IllegalStateException("Should never be called")
}

internal class IncrementalKotlinBuilder(
  private val dataManager: BazelBuildDataProvider,
  private val jpsTarget: BazelModuleBuildTarget,
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
    if (isRebuild || kotlinContext.hasKotlinMarker.get(chunk.targets.single()) != true) {
      return
    }

    val kotlinChunk = kotlinContext.getChunk(chunk) ?: return
    if (kotlinChunk.isEnabled) {
      markAdditionalFilesForInitialRound(
        kotlinChunk = kotlinChunk,
        chunk = chunk,
        kotlinContext = kotlinContext,
        moduleTarget = jpsTarget,
        span = span,
      )
    }
  }

  private fun markAdditionalFilesForInitialRound(
    kotlinChunk: KotlinChunk,
    chunk: ModuleChunk,
    kotlinContext: KotlinCompileContext,
    moduleTarget: BazelModuleBuildTarget,
    span: Span,
  ) {
    val context = kotlinContext.jpsContext
    val dirtyFilesHolder = KotlinDirtySourceFilesHolder(
      chunk = chunk,
      context = context,
      delegate = object : DirtyFilesHolderBase<JavaSourceRootDescriptor, ModuleBuildTarget>(context) {
        override fun processDirtyFiles(processor: FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>) {
          context.projectDescriptor.fsState.processFilesToRecompile(context, chunk.targets.single(), processor)
        }
      },
    )

    val removedClasses = hashSet<String>()
    // dependent caches are not required, since we are not going to update caches
    val incrementalCaches = kotlinChunk.loadCaches(loadDependent = false)
    val targetDirtyFiles = dirtyFilesHolder.byTarget.get(moduleTarget)
    for (target in kotlinChunk.targets) {
      val cache = incrementalCaches.get(target) ?: continue
      val dirtyFiles = targetDirtyFiles?.dirty?.keys ?: emptySet()
      val removedFiles = targetDirtyFiles?.removed ?: emptyList()

      val existingClasses = classesFqNames(dirtyFiles)
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

    markFilesForCurrentRound(
      files = affectedByRemovedClasses.dirtyFiles.concat(affectedByRemovedClasses.forceRecompileTogether),
      targetDirtyFiles = targetDirtyFiles,
      span = span,
      context = context,
      target = moduleTarget,
      dataManager = dataManager,
    )
  }

  override suspend fun build(
    context: BazelCompileContext,
    module: JpsModule,
    chunk: ModuleChunk,
    target: BazelModuleBuildTarget,
    dirtyFilesHolder: BazelDirtyFileHolder,
    outputConsumer: BazelTargetBuildOutputConsumer,
    outputSink: OutputSink,
  ): ModuleLevelBuilder.ExitCode {
    val kotlinContext = getKotlinCompileContext(context)
    val kotlinTarget = kotlinContext.targetsIndex.byJpsTarget.get(jpsTarget) ?: return ModuleLevelBuilder.ExitCode.OK
    val fsOperations = BazelKotlinFsOperationsHelper(context = context, chunk = chunk)
    val proposedExitCode = doBuild(
      chunk = chunk,
      representativeTarget = kotlinTarget,
      context = context,
      dirtyFilesHolder = dirtyFilesHolder,
      messageCollector = MessageCollectorAdapter(context, span, kotlinTarget),
      outputConsumer = outputConsumer,
      fsOperations = fsOperations,
      kotlinContext = kotlinContext,
    )

    val actualExitCode = if (proposedExitCode == ModuleLevelBuilder.ExitCode.OK && fsOperations.hasMarkedDirty) {
      ModuleLevelBuilder.ExitCode.ADDITIONAL_PASS_REQUIRED
    }
    else {
      proposedExitCode
    }
    context.testingContext?.buildLogger?.buildFinished(actualExitCode)
    return actualExitCode
  }

  private suspend fun doBuild(
    chunk: ModuleChunk,
    representativeTarget: KotlinModuleBuildTarget<*>,
    context: CompileContext,
    dirtyFilesHolder: BazelDirtyFileHolder,
    messageCollector: MessageCollectorAdapter,
    outputConsumer: BazelTargetBuildOutputConsumer,
    fsOperations: BazelKotlinFsOperationsHelper,
    kotlinContext: KotlinCompileContext,
  ): ExitCode {
    val kotlinChunk = kotlinContext.getChunk(chunk)!!
    if (!kotlinChunk.isEnabled) {
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE
    }

    val target = chunk.targets.single()

    val isChunkRebuilding = isRebuild || kotlinContext.rebuildAfterCacheVersionChanged.get(target) == true

    val kotlinDirtyFilesHolder = KotlinDirtySourceFilesHolder(chunk, context, dirtyFilesHolder)
    val dirtyByTarget = kotlinDirtyFilesHolder.byTarget.get(jpsTarget)
    if (dirtyByTarget == null || (dirtyByTarget.removed.isEmpty() && dirtyByTarget.dirty.isEmpty())) {
      if (isChunkRebuilding) {
        kotlinContext.hasKotlinMarker.set(target, false)
      }

      kotlinContext.rebuildAfterCacheVersionChanged.clean(target)
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE
    }

    val project = context.projectDescriptor.project
    val lookupTracker = LookupTrackerImpl(project.testingContext?.lookupTracker ?: LookupTracker.DO_NOTHING)
    val incrementalCaches = kotlinChunk.loadCaches()

    val outputItemCollector = OutputItemsCollectorImpl()
    val expectActualTracer = ExpectActualTrackerImpl()
    val environment = createCompileEnvironment(
      context = context,
      outputItemCollector = outputItemCollector,
      kotlinModuleBuilderTarget = representativeTarget,
      incrementalCaches = incrementalCaches,
      lookupTracker = lookupTracker,
      exceptActualTracer = expectActualTracer,
      inlineConstTracker = InlineConstTrackerImpl(),
      enumWhenTracker = EnumWhenTrackerImpl(),
      importTracker = ImportTrackerImpl(),
      chunk = chunk,
      messageCollector = messageCollector
    ) ?: return ModuleLevelBuilder.ExitCode.ABORT

    val generatedFiles = doCompileModuleChunk(
      chunk = kotlinChunk,
      outputItemCollector = outputItemCollector,
      context = context,
      targetDirtyFiles = dirtyByTarget,
      fsOperations = fsOperations,
      environment = environment,
      incrementalCaches = incrementalCaches,
      messageCollector = messageCollector,
      outputConsumer = outputConsumer,
      span = span,
    )

    if (generatedFiles == null) {
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE
    }

    val compilationErrors = Utils.ERRORS_DETECTED_KEY.get(context, false)
    if (compilationErrors) {
      JavaBuilderUtil.registerFilesWithErrors(context, messageCollector.filesWithErrors.map { it.toFile() })
      return ModuleLevelBuilder.ExitCode.ABORT
    }
    else {
      JavaBuilderUtil.registerSuccessfullyCompiled(context, dirtyByTarget.dirty.keys.toList())
    }

    val cache = incrementalCaches.get(representativeTarget)
    if (cache is IncrementalJvmCache) {
      markDirtyComplementaryMultifileClasses(
        files = generatedFiles,
        cache = cache,
        fsOperations = fsOperations,
        target = jpsTarget,
        dataManager = dataManager,
        span = span,
      )
    }

    // we do not save cache version - TargetConfigurationDigestProperty.KOTLIN_VERSION is used to rebuild in case of kotlinc update

    if (kotlinContext.hasKotlinMarker.get(target) == null) {
      fsOperations.markChunk(context = context, excludeFiles = dirtyByTarget.dirty.keys, dataManager = dataManager)
    }

    kotlinContext.hasKotlinMarker.set(target, true)
    kotlinContext.rebuildAfterCacheVersionChanged.clean(target)

    kotlinChunk.targets.single().doAfterBuild()

    representativeTarget.updateChunkMappings(
      localContext = context,
      chunk = chunk,
      dirtyFilesHolder = kotlinDirtyFilesHolder,
      outputItems = mapOf(target to generatedFiles),
      incrementalCaches = incrementalCaches,
      environment = environment
    )

    coroutineContext.ensureActive()

    val jpsIncrementalCache = incrementalCaches.get(representativeTarget)!! as IncrementalJvmCache

    jpsIncrementalCache.updateComplementaryFiles(
      dirtyFiles = dirtyByTarget.dirty.keys + dirtyByTarget.removed,
      expectActualTracker = expectActualTracer,
    )

    val changeCollector = ChangesCollector()
    for (generatedFile in generatedFiles) {
      if (generatedFile is GeneratedJvmClass) {
        jpsIncrementalCache.saveFileToCache(generatedFile, changesCollector = changeCollector)
      }
      else if (generatedFile.outputFile.isModuleMappingFile()) {
        val tempFile = Files.createTempFile(dataManager.storeFile.parent, "kotlin_module-", "")
        try {
          Files.write(tempFile, generatedFile.data)
          jpsIncrementalCache.saveModuleMappingToCache(sourceFiles = generatedFile.sourceFiles, file = tempFile.toFile())
        }
        finally {
          Files.deleteIfExists(tempFile)
        }
      }
    }
    jpsIncrementalCache.clearCacheForRemovedClasses(changeCollector)

    updateLookupStorage(lookupTracker, kotlinContext.lookupStorageManager, dirtyByTarget)

    if (!isChunkRebuilding) {
      val dirtyFilesAsPathList = dirtyByTarget.dirty.keys.mapTo(hashSet(dirtyByTarget.dirty.keys.size)) { it.toPath() }
      doProcessChangesUsingLookups(
        collector = changeCollector,
        compiledFiles = dirtyFilesAsPathList,
        lookupStorageManager = kotlinContext.lookupStorageManager,
        fsOperations = fsOperations,
        caches = incrementalCaches.values,
        reporter = BazelJpsICReporter(span),
        target = jpsTarget,
        dataManager = dataManager,
        span = span,
      )
    }

    return ModuleLevelBuilder.ExitCode.OK
  }
}

// todo(1.2.80): got rid of ModuleChunk (replace with KotlinChunk)
// todo(1.2.80): introduce KotlinRoundCompileContext, move dirtyFilesHolder, fsOperations, environment to it
private fun doCompileModuleChunk(
  chunk: KotlinChunk,
  context: CompileContext,
  targetDirtyFiles: TargetFiles?,
  fsOperations: BazelKotlinFsOperationsHelper,
  environment: JpsCompilerEnvironment,
  incrementalCaches: Map<KotlinModuleBuildTarget<*>, JpsIncrementalCache>,
  messageCollector: MessageCollectorAdapter,
  outputConsumer: BazelTargetBuildOutputConsumer,
  outputItemCollector: OutputItemsCollectorImpl,
  span: Span,
): List<GeneratedFile>? {
  val target = chunk.targets.single() as KotlinJvmModuleBuildTarget
  target.nextRound(context)

  val cache = incrementalCaches.get(target)
  val jpsTarget = target.jpsModuleBuildTarget as BazelModuleBuildTarget

  if (cache != null && targetDirtyFiles != null) {
    val dirtyFiles = targetDirtyFiles.dirty.keys.concat(targetDirtyFiles.removed)
    val complementaryFiles = cache.getComplementaryFilesRecursive(dirtyFiles)
    context.testingContext?.buildLogger?.markedAsComplementaryFiles(complementaryFiles.toList())
    fsOperations.markFilesForCurrentRound(
      files = complementaryFiles.map { it.toPath() },
      targetDirtyFiles = targetDirtyFiles,
      parentSpan = span,
      outputSink = outputConsumer.outputSink,
      dataManager = outputConsumer.dataManager!!,
      target = jpsTarget,
    )

    cache.markDirty(dirtyFiles)
  }

  if (targetDirtyFiles != null) {
    val allDirtyFiles = targetDirtyFiles.dirty.keys
    if (span.isRecording) {
      span.addEvent("compiling files", Attributes.of(AttributeKey.stringArrayKey("allDirtyFiles"), allDirtyFiles.map { it.path }))
    }
    registerFilesToCompile(context, allDirtyFiles)
  }

  val filesSet = targetDirtyFiles?.dirty?.keys ?: emptySet()
  val changedSources = targetDirtyFiles?.dirty?.values ?: emptyList()
  val removedFiles = targetDirtyFiles?.removed ?: emptyList()
  if (changedSources.isEmpty() && removedFiles.isEmpty()) {
    span.addEvent("not compiling, because no files affected")
    return null
  }

  if (span.isRecording) {
    span.addEvent("compiling", Attributes.of(
      AttributeKey.stringArrayKey("files"), changedSources.map { it.file.path },
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
    allSources = bazelConfigurationHolder.sources,
    changedKotlinSources = changedSources.asSequence().map { it.file.path },
    classPath = bazelConfigurationHolder.classPath.asList(),
  )

  var outputs: List<OutputFile> = emptyList()
  val pipeline = createJvmPipeline(config) {
    outputs = it.asList()
    outputConsumer.registerKotlincOutput(context, outputs)
  }

  val exitCode = executeJvmPipeline(pipeline, bazelConfigurationHolder.kotlinArgs, environment.services, messageCollector)
  @Suppress("RemoveRedundantQualifierName")
  if (org.jetbrains.kotlin.cli.common.ExitCode.INTERNAL_ERROR == exitCode) {
    messageCollector.report(CompilerMessageSeverity.ERROR, "Compiler terminated with internal error")
  }

  require(outputItemCollector.outputs.isEmpty()) {
    throw IllegalStateException("Not expected that outputItemCollector is used: ${outputItemCollector.outputs}")
  }

  val result = Array(outputs.size) {
    val output = outputs.get(it)
    if (output.relativePath.endsWith(".class")) {
      GeneratedJvmClass(
        data = output.asByteArray(),
        sourceFiles = output.sourceFiles,
        outputFile = File(output.relativePath),
        metadataVersionFromLanguageVersion = MetadataVersion.INSTANCE,
      )
    }
    else {
      GeneratedFile(sourceFiles = output.sourceFiles, outputFile = File(output.relativePath), data = output.asByteArray())
    }
  }
  result.sortBy { it.outputFile.path }
  return result.asList()
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
  messageCollector: MessageCollectorAdapter,
  outputItemCollector: OutputItemsCollectorImpl
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
    outputItemsCollector = outputItemCollector,
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
  files: List<GeneratedFile>,
  cache: IncrementalJvmCache,
  fsOperations: BazelKotlinFsOperationsHelper,
  target: BazelModuleBuildTarget,
  dataManager: BazelBuildDataProvider,
  span: Span,
) {
  val multifileClasses = files
    .asSequence()
    .filterIsInstance<GeneratedJvmClass>()
    .filter { it.outputClass.classHeader.kind == KotlinClassHeader.Kind.MULTIFILE_CLASS }
    .toList()
  if (multifileClasses.isEmpty()) {
    return
  }

  val actualParts = files
    .asSequence()
    .filterIsInstance<GeneratedJvmClass>()
    .filter { it.outputClass.classHeader.kind == KotlinClassHeader.Kind.MULTIFILE_CLASS_PART }
    .map { it.outputClass.className.toString() }
    .toList()

  val expectedAllParts = multifileClasses.flatMap { cache.getAllPartsOfMultifileFacade(it.outputClass.className).orEmpty() }
  if (!actualParts.containsAll(expectedAllParts)) {
    fsOperations.markFiles(
      files = (
        expectedAllParts.asSequence().flatMap { cache.sourcesByInternalName(it) }
          + multifileClasses.asSequence().flatMap { it.sourceFiles }
        )
        .map { it.toPath() }
        .filterTo(linkedSet()) { Files.exists(it) },
      currentRound = false,
      target = target,
      dataManager = dataManager,
      span = span,
    )
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
  target: BazelModuleBuildTarget,
  dataManager: BazelBuildDataProvider,
  span: Span,
) {
  val dirtyFiles = getDirtyFiles(
    changeCollector = collector,
    caches = caches.flatMap { it.thisWithDependentCaches },
    lookupStorageManager = lookupStorageManager,
    reporter = reporter,
  )
  // If a list of inheritors of sealed class has changed, it should be recompiled with all the inheritors
  // Here we have a small optimization. Do not recompile the bunch if ALL these files were recompiled during the previous round.
  val forceRecompileTogether = dirtyFiles.forceRecompileTogether
  val excludeFiles = if (forceRecompileTogether.isEmpty() || compiledFiles.containsAll(forceRecompileTogether.map { it.toPath() })) {
    compiledFiles
  }
  else {
    val result = HashSet(compiledFiles)
    for (file in forceRecompileTogether) {
      result.remove(file.toPath())
    }
    result
  }

  fsOperations.markFiles(
    files = (dirtyFiles.dirtyFiles.asSequence() + forceRecompileTogether.asSequence())
      .map { it.toPath() }
      .filterTo(linkedSet()) { !excludeFiles.contains(it) && Files.exists(it) },
    currentRound = false,
    dataManager = dataManager,
    target = target,
    span = span,
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
    forceRecompileTogether = mapClassesFqNamesToFiles(caches, forceRecompile, reporter),
  )
}