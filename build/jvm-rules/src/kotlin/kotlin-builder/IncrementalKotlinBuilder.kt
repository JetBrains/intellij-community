// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet", "PackageDirectoryMismatch")

package org.jetbrains.bazel.jvm.kotlin

import androidx.collection.MutableScatterSet
import androidx.collection.ScatterSet
import com.intellij.openapi.vfs.VirtualFile
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.ensureActive
import org.jetbrains.bazel.jvm.util.concat
import org.jetbrains.bazel.jvm.util.emptyList
import org.jetbrains.bazel.jvm.util.emptySet
import org.jetbrains.bazel.jvm.worker.core.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.worker.core.BazelBuildDataProvider
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelDirtyFileHolder
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.worker.core.BazelTargetBuildOutputConsumer
import org.jetbrains.bazel.jvm.worker.core.output.OutputSink
import org.jetbrains.bazel.jvm.kotlin.configureModule
import org.jetbrains.bazel.jvm.kotlin.createJvmPipeline
import org.jetbrains.bazel.jvm.kotlin.executeJvmPipeline
import org.jetbrains.bazel.jvm.kotlin.prepareCompilerConfiguration
import org.jetbrains.bazel.jvm.util.linkedSet
import org.jetbrains.bazel.jvm.span
import org.jetbrains.bazel.jvm.worker.core.BazelTargetBuilder
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaBuilderUtil.registerFilesToCompile
import org.jetbrains.jps.builders.java.dependencyView.Callbacks
import org.jetbrains.jps.builders.java.dependencyView.Callbacks.Backend
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.kotlin.build.GeneratedFile
import org.jetbrains.kotlin.build.GeneratedJvmClass
import org.jetbrains.kotlin.build.isModuleMappingFile
import org.jetbrains.kotlin.build.report.ICReporter
import org.jetbrains.kotlin.build.report.ICReporter.ReportSeverity
import org.jetbrains.kotlin.build.report.ICReporterBase
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.compiler.PsiBasedProjectFileSearchScope
import org.jetbrains.kotlin.cli.jvm.compiler.VfsBasedProjectEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.ProjectFileSearchScopeProvider
import org.jetbrains.kotlin.cli.jvm.config.ClassicFrontendSpecificJvmConfigurationKeys
import org.jetbrains.kotlin.cli.jvm.config.VirtualJvmClasspathRoot
import org.jetbrains.kotlin.compilerRunner.JpsCompilerEnvironment
import org.jetbrains.kotlin.compilerRunner.OutputItemsCollectorImpl
import org.jetbrains.kotlin.compilerRunner.ProgressReporterImpl
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.config.enumWhenTracker
import org.jetbrains.kotlin.config.expectActualTracker
import org.jetbrains.kotlin.config.importTracker
import org.jetbrains.kotlin.config.incrementalCompilationComponents
import org.jetbrains.kotlin.config.inlineConstTracker
import org.jetbrains.kotlin.config.lookupTracker
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectFileSearchScope
import org.jetbrains.kotlin.incremental.ChangesCollector
import org.jetbrains.kotlin.incremental.EnumWhenTrackerImpl
import org.jetbrains.kotlin.incremental.ExpectActualTrackerImpl
import org.jetbrains.kotlin.incremental.ImportTrackerImpl
import org.jetbrains.kotlin.incremental.IncrementalCacheCommon
import org.jetbrains.kotlin.incremental.IncrementalJvmCache
import org.jetbrains.kotlin.incremental.InlineConstTrackerImpl
import org.jetbrains.kotlin.incremental.KotlinClassInfo
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
import org.jetbrains.kotlin.jps.targets.impl.LookupUsageRegistrar
import org.jetbrains.kotlin.load.java.JavaClassesTracker
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCache
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCompilationComponents
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.modules.TargetId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.preloading.ClassCondition
import org.jetbrains.kotlin.progress.CompilationCanceledStatus
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.org.objectweb.asm.ClassReader
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

private val classesToLoadByParent = ClassCondition { className ->
  throw IllegalStateException("Should never be called")
}

class IncrementalKotlinBuilder(
  private val dataManager: BazelBuildDataProvider,
  private val jpsTarget: BazelModuleBuildTarget,
  private val isRebuild: Boolean,
  private val span: Span,
  private val tracer: Tracer,
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
      delegate = BazelDirtyFileHolder(context, context.projectDescriptor.fsState, moduleTarget),
    )

    val removedClasses = MutableScatterSet<String>()
    // dependent caches are not required, since we are not going to update caches
    val incrementalCaches = kotlinChunk.loadCaches(loadDependent = false)
    val targetDirtyFiles = dirtyFilesHolder.byTarget.get(moduleTarget)
    for (target in kotlinChunk.targets) {
      val cache = incrementalCaches.get(target) ?: continue
      val dirtyFiles = targetDirtyFiles?.dirty?.keys ?: emptySet()
      val removedFiles = targetDirtyFiles?.removed ?: emptyList()
      val previousClasses = cache.classesFqNamesBySources(dirtyFiles.concat(removedFiles))
      if (previousClasses.isNotEmpty()) {
        val existingClasses = classesFqNames(dirtyFiles)
        for (jvmClassName in previousClasses) {
          val fqName = jvmClassName.asString()
          if (!existingClasses.contains(fqName)) {
            removedClasses.add(fqName)
          }
        }
      }
    }

    val changeCollector = ChangesCollector()
    removedClasses.forEach {
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
    val proposedExitCode = tracer.span("compile kotlin") { span ->
      doBuild(
        chunk = chunk,
        representativeTarget = kotlinTarget,
        context = context,
        dirtyFilesHolder = dirtyFilesHolder,
        outputConsumer = outputConsumer,
        fsOperations = fsOperations,
        kotlinContext = kotlinContext,
        outputSink = outputSink,
        span = span,
      )
    }
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
    context: BazelCompileContext,
    dirtyFilesHolder: BazelDirtyFileHolder,
    outputConsumer: BazelTargetBuildOutputConsumer,
    fsOperations: BazelKotlinFsOperationsHelper,
    kotlinContext: KotlinCompileContext,
    outputSink: OutputSink,
    span: Span,
  ): ExitCode {
    val target = chunk.targets.single()

    val messageCollector = MessageCollectorAdapter(
      context = context,
      span = span,
      kotlinTarget = representativeTarget,
      skipWarns = target.module.container.getChild(BazelConfigurationHolder.KIND)!!.kotlinArgs.let { it.suppressWarnings && !it.allWarningsAsErrors }
    )

    val kotlinChunk = kotlinContext.getChunk(chunk)!!
    if (!kotlinChunk.isEnabled) {
      return ModuleLevelBuilder.ExitCode.NOTHING_DONE
    }

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

    val fs = OutputFileSystem(outputSink)
    val prevOutputVirtualDir = fs.root

    val outputItemCollector = OutputItemsCollectorImpl()
    val expectActualTracer = ExpectActualTrackerImpl()
    val environment = createCompileEnvironment(
      prevOutputVirtualDir = prevOutputVirtualDir,
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
      prevOutputVirtualDir = prevOutputVirtualDir,
      chunk = kotlinChunk,
      outputItemCollector = outputItemCollector,
      context = context,
      targetDirtyFiles = dirtyByTarget,
      fsOperations = fsOperations,
      services = environment.services,
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

    updateChunkMappings(localContext = context, outputItems = generatedFiles, environment = environment)

    coroutineContext.ensureActive()

    val jpsIncrementalCache = incrementalCaches.get(representativeTarget)!! as IncrementalJvmCache

    jpsIncrementalCache.updateComplementaryFiles(
      dirtyFiles = dirtyByTarget.dirty.keys.concat(dirtyByTarget.removed),
      expectActualTracker = expectActualTracer,
    )

    val changeCollector = ChangesCollector()
    // updateCaches
    for (generatedFile in generatedFiles) {
      if (generatedFile is GeneratedJvmClass) {
        jpsIncrementalCache.saveClassToCache(
          kotlinClassInfo = KotlinClassInfo.createFrom(generatedFile.outputClass),
          sourceFiles = generatedFile.sourceFiles,
          changesCollector = changeCollector,
        )
      }
      else if (generatedFile.outputFile.isModuleMappingFile()) {
        jpsIncrementalCache.saveModuleMappingToCache(sourceFiles = generatedFile.sourceFiles, data = generatedFile.data)
      }
    }
    jpsIncrementalCache.clearCacheForRemovedClasses(changeCollector)

    updateLookupStorage(lookupTracker, kotlinContext.lookupStorageManager, dirtyByTarget)

    if (!isChunkRebuilding) {
      val dirtyFilesAsPathList = MutableScatterSet<Path>(dirtyByTarget.dirty.keys.size)
      for (file in dirtyByTarget.dirty.keys) {
        dirtyFilesAsPathList.add(file.toPath())
      }
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

private fun updateChunkMappings(
  localContext: CompileContext,
  outputItems: List<GeneratedFile>,
  environment: JpsCompilerEnvironment
) {
  val callback = JavaBuilderUtil.getDependenciesRegistrar(localContext)
  val inlineConstTracker = environment.services[InlineConstTracker::class.java] as InlineConstTrackerImpl
  val enumWhenTracker = environment.services[EnumWhenTracker::class.java] as EnumWhenTrackerImpl
  val importTracker = environment.services[ImportTracker::class.java] as ImportTrackerImpl

  LookupUsageRegistrar().processLookupTracker(
    environment.services[LookupTracker::class.java],
    callback,
    environment.messageCollector
  )

  for (output in outputItems) {
    if (output !is GeneratedJvmClass) {
      continue
    }

    val sourceFiles = output.sourceFiles
    // process trackers
    for (sourceFile in sourceFiles) {
      processInlineConstTracker(inlineConstTracker, sourceFile, output, callback)
      processBothEnumWhenAndImportTrackers(enumWhenTracker, importTracker, sourceFile, output, callback)
    }

    callback.associate(
      output.relativePath,
      sourceFiles.map { it.invariantSeparatorsPath },
      ClassReader(output.outputClass.fileContents)
    )
  }
  // important: in jps-dependency-graph you can't register additional dependencies after [callback.associate].
}

private fun processInlineConstTracker(inlineConstTracker: InlineConstTrackerImpl, sourceFile: File, output: GeneratedJvmClass, callback: Backend) {
  val cRefs = inlineConstTracker.inlineConstMap.get(sourceFile.path)?.mapNotNull { cRef ->
    @Suppress("SpellCheckingInspection")
    val descriptor = when (cRef.constType) {
      "Byte" -> "B"
      "Short" -> "S"
      "Int" -> "I"
      "Long" -> "J"
      "Float" -> "F"
      "Double" -> "D"
      "Boolean" -> "Z"
      "Char" -> "C"
      "String" -> "Ljava/lang/String;"
      else -> null
    } ?: return@mapNotNull null
    Callbacks.createConstantReference(cRef.owner, cRef.name, descriptor)
  } ?: return

  val className = output.outputClass.className.internalName
  callback.registerConstantReferences(className, cRefs)
}

private fun processBothEnumWhenAndImportTrackers(
  enumWhenTracker: EnumWhenTrackerImpl,
  importTracker: ImportTrackerImpl,
  sourceFile: File,
  output: GeneratedJvmClass,
  callback: Backend
) {
  val enumFqNameClasses = enumWhenTracker.whenExpressionFilePathToEnumClassMap[sourceFile.path]?.map { "$it.*" }
  val importedFqNames = importTracker.filePathToImportedFqNamesMap[sourceFile.path]
  if (enumFqNameClasses == null && importedFqNames == null) return

  callback.registerImports(output.outputClass.className.internalName, importedFqNames ?: listOf(), enumFqNameClasses ?: listOf())
}

private fun setupIncrementalCompilationServices(services: Services, config: CompilerConfiguration) {
  config.lookupTracker = services.get(LookupTracker::class.java)
  config.expectActualTracker = services.get(ExpectActualTracker::class.java)
  config.inlineConstTracker = services.get(InlineConstTracker::class.java)
  config.enumWhenTracker = services.get(EnumWhenTracker::class.java)
  config.importTracker = services.get(ImportTracker::class.java)
  config.incrementalCompilationComponents = services.get(IncrementalCompilationComponents::class.java)
  config.putIfNotNull(ClassicFrontendSpecificJvmConfigurationKeys.JAVA_CLASSES_TRACKER, services.get(JavaClassesTracker::class.java))
}

private suspend fun doCompileModuleChunk(
  chunk: KotlinChunk,
  context: BazelCompileContext,
  targetDirtyFiles: TargetFiles?,
  fsOperations: BazelKotlinFsOperationsHelper,
  services: Services,
  incrementalCaches: Map<KotlinModuleBuildTarget<*>, JpsIncrementalCache>,
  messageCollector: MessageCollectorAdapter,
  outputConsumer: BazelTargetBuildOutputConsumer,
  outputItemCollector: OutputItemsCollectorImpl,
  prevOutputVirtualDir: VirtualFile,
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
    if (!context.scope.isRebuild && span.isRecording) {
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
    abiOutputConsumer = {
      outputConsumer.registerKotlincAbiOutput(it)
    },
  )
  setupIncrementalCompilationServices(services, config)

  config.add(CLIConfigurationKeys.CONTENT_ROOTS, VirtualJvmClasspathRoot(prevOutputVirtualDir, isSdkRoot = false, isFriend = true))

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

  val coroutineContext = coroutineContext
  val result = ArrayList<GeneratedFile>()
  val pipeline = createJvmPipeline(config, checkCancelled = { coroutineContext.ensureActive() }) {
    val outputs = it.asList()

    result.ensureCapacity(outputs.size)
    for (output in outputs) {
      val relativePath = output.relativePath.replace(File.separatorChar, '/')
      val file = if (relativePath.endsWith(".class")) {
        GeneratedJvmClass(
          relativePath = relativePath,
          data = output.asByteArray(),
          sourceFiles = output.sourceFiles,
          outputFile = File(relativePath),
          metadataVersionFromLanguageVersion = MetadataVersion.INSTANCE,
        )
      }
      else {
        GeneratedFile(
          relativePath = relativePath,
          sourceFiles = output.sourceFiles,
          outputFile = File(relativePath),
          data = output.asByteArray(),
        )
      }
      result.add(file)
    }

    outputConsumer.registerIncrementalKotlincOutput(context, result)
  }

  val exitCode = executeJvmPipeline(pipeline, bazelConfigurationHolder.kotlinArgs, services, messageCollector)
  @Suppress("RemoveRedundantQualifierName")
  if (org.jetbrains.kotlin.cli.common.ExitCode.INTERNAL_ERROR == exitCode) {
    messageCollector.report(CompilerMessageSeverity.ERROR, "Compiler terminated with internal error")
  }

  require(outputItemCollector.outputs.isEmpty()) {
    throw IllegalStateException("Not expected that outputItemCollector is used: ${outputItemCollector.outputs}")
  }

  result.sortBy { it.outputFile.path }
  return result
}

private fun createCompileEnvironment(
  prevOutputVirtualDir: OutputVirtualFile,
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
      kotlinModuleBuilderTarget.jpsGlobalContext.checkCanceled()
    }
  })
  builder.register(InlineConstTracker::class.java, implementation = inlineConstTracker)
  builder.register(EnumWhenTracker::class.java, implementation = enumWhenTracker)
  builder.register(ImportTracker::class.java, implementation = importTracker)
  builder.register(
    IncrementalCompilationComponents::class.java,
    @Suppress("UNCHECKED_CAST")
    BazelIncrementalCompilationComponentsImpl(prevOutputVirtualDir, incrementalCaches.mapKeys { it.key.targetId } as Map<TargetId, IncrementalCache>)
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

private class BazelIncrementalCompilationComponentsImpl(
  private val vf: OutputVirtualFile,
  private val caches: Map<TargetId, IncrementalCache>
) : IncrementalCompilationComponents, ProjectFileSearchScopeProvider {
  override fun getIncrementalCache(target: TargetId): IncrementalCache {
    return requireNotNull(caches.get(target)) { "Incremental cache for target ${target.name} not found" }
  }

  override fun createSearchScope(projectEnvironment: VfsBasedProjectEnvironment): AbstractProjectFileSearchScope {
    return PsiBasedProjectFileSearchScope(KotlinToJVMBytecodeCompiler.DirectoriesScope(projectEnvironment.project, setOf(vf)))
  }
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
  compiledFiles: ScatterSet<Path>,
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
  val excludeFiles = if (forceRecompileTogether.isEmpty() || forceRecompileTogether.all { compiledFiles.contains(it.toPath()) }) {
    compiledFiles
  }
  else {
    val result = MutableScatterSet<Path>(compiledFiles.size)
    result.addAll(compiledFiles)
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

private object DummyKotlinPaths : KotlinPaths {
  override val homePath: File
    get() = throw kotlin.IllegalStateException()

  override val libPath: File
    get() = throw kotlin.IllegalStateException()

  override fun jar(jar: KotlinPaths.Jar): File = throw kotlin.IllegalStateException()

  override fun klib(jar: KotlinPaths.Jar): File = throw kotlin.IllegalStateException()

  override fun sourcesJar(jar: KotlinPaths.Jar): File? = throw kotlin.IllegalStateException()
}