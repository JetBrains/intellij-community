@file:Suppress("HardCodedStringLiteral", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")
package org.jetbrains.bazel.jvm.jps.impl

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.FileProcessor
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.incremental.ModuleLevelBuilder.ExitCode.*
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.kotlin.build.GeneratedFile
import org.jetbrains.kotlin.build.GeneratedJvmClass
import org.jetbrains.kotlin.build.report.ICReporter
import org.jetbrains.kotlin.build.report.ICReporter.ReportSeverity
import org.jetbrains.kotlin.build.report.ICReporterBase
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.compilerRunner.*
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.incremental.*
import org.jetbrains.kotlin.incremental.components.*
import org.jetbrains.kotlin.jps.build.*
import org.jetbrains.kotlin.jps.incremental.JpsIncrementalCache
import org.jetbrains.kotlin.jps.incremental.JpsLookupStorageManager
import org.jetbrains.kotlin.jps.targets.KotlinModuleBuildTarget
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.preloading.ClassCondition
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.kotlin.utils.KotlinPathsFromHomeDir
import java.io.File
import kotlin.system.measureTimeMillis

private val classesToLoadByParent = ClassCondition { className ->
  for (it in arrayOf(
    "org.apache.log4j.", // For logging from compiler
    "org.jetbrains.kotlin.incremental.components.",
    "org.jetbrains.kotlin.incremental.js",
    "org.jetbrains.kotlin.load.kotlin.incremental.components."
  )) {
    if (className.startsWith(it)) {
      return@ClassCondition true
    }
  }
  for (it in arrayOf(
    "org.jetbrains.kotlin.config.Services",
    "org.jetbrains.kotlin.progress.CompilationCanceledStatus",
    "org.jetbrains.kotlin.progress.CompilationCanceledException",
    "org.jetbrains.kotlin.modules.TargetId",
    "org.jetbrains.kotlin.cli.common.ExitCode"
  )) {
    if (className == it) {
      return@ClassCondition true
    }
  }

  return@ClassCondition false
}

internal class BazelKotlinBuilder(
  private val isKotlinBuilderInDumbMode: Boolean,
  private val enableLookupStorageFillingInDumbMode: Boolean = false,
  private val dataManager: BazelBuildDataProvider,
  private val span: Span,
) : ModuleLevelBuilder(BuilderCategory.SOURCE_PROCESSOR) {
  companion object {
    const val JPS_KOTLIN_HOME_PROPERTY = "jps.kotlin.home"
  }

  override fun getPresentableName() = "Kotlin Builder"

  override fun getCompilableFileExtensions() = arrayListOf("kt")

  /**
   * Ensure Kotlin Context initialized.
   * Kotlin Context should be initialized only when required (before the first kotlin chunk build).
   */
  private fun ensureKotlinContextInitialized(context: CompileContext): KotlinCompileContext {
    context.getUserData(kotlinCompileContextKey)?.let {
      return it
    }

    // don't synchronize on context, since it is chunk local only
    synchronized(kotlinCompileContextKey) {
      context.getUserData(kotlinCompileContextKey)?.let {
        return it
      }
      return initializeKotlinContext(context)
    }
  }

  private fun initializeKotlinContext(context: CompileContext): KotlinCompileContext {
    lateinit var kotlinContext: KotlinCompileContext

    val time = measureTimeMillis {
      kotlinContext = KotlinCompileContext(context)

      context.putUserData(kotlinCompileContextKey, kotlinContext)
      context.testingContext?.kotlinCompileContext = kotlinContext

      if (kotlinContext.shouldCheckCacheVersions && kotlinContext.hasKotlin()) {
        kotlinContext.checkCacheVersions()
      }

      kotlinContext.cleanupCaches()
      kotlinContext.reportUnsupportedTargets()
    }

    span.addEvent("total Kotlin global compile context initialization time: $time ms")

    return kotlinContext
  }

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
    super.chunkBuildStarted(context, chunk)

    val buildLogger = context.testingContext?.buildLogger
    buildLogger?.chunkBuildStarted(context, chunk)

    if (JavaBuilderUtil.isForcedRecompilationAllJavaModules(context)) {
      return
    }

    val targets = chunk.targets
    val kotlinContext = ensureKotlinContextInitialized(context)
    if (targets.none { kotlinContext.hasKotlinMarker[it] == true }) {
      return
    }

    val kotlinChunk = kotlinContext.getChunk(chunk) ?: return
    kotlinContext.checkChunkCacheVersion(kotlinChunk)

    if (!isKotlinBuilderInDumbMode && !kotlinContext.rebuildingAllKotlin && kotlinChunk.isEnabled) {
      markAdditionalFilesForInitialRound(kotlinChunk, chunk, kotlinContext)
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
    val fsOperations = BazelKotlinFsOperationsHelper(context, chunk, dirtyFilesHolder, span, dataManager = dataManager)

    val representativeTarget = kotlinContext.targetsBinding[chunk.representativeTarget()] ?: return

    // dependent caches are not required, since we are not going to update caches
    val incrementalCaches = kotlinChunk.loadCaches(loadDependent = false)

    val messageCollector = MessageCollectorAdapter(context, representativeTarget)
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
      val cache = incrementalCaches[target] ?: continue
      val dirtyFiles = dirtyFilesHolder.getDirtyFiles(target.jpsModuleBuildTarget).keys
      val removedFiles = dirtyFilesHolder.getRemovedFiles(target.jpsModuleBuildTarget)

      val existingClasses = JpsKotlinCompilerRunner().classesFqNamesByFiles(environment, dirtyFiles)
      val previousClasses = cache.classesFqNamesBySources(dirtyFiles + removedFiles)
      for (jvmClassName in previousClasses) {
        val fqName = jvmClassName.asString()
        if (fqName !in existingClasses) {
          removedClasses.add(fqName)
        }
      }
    }

    val changesCollector = ChangesCollector()
    for (it in removedClasses) {
      changesCollector.collectSignature(FqName(it), areSubclassesAffected = true)
    }
    val affectedByRemovedClasses = getDirtyFiles(
      changeCollector = changesCollector,
      caches = incrementalCaches.values,
      lookupStorageManager = kotlinContext.lookupStorageManager,
      reporter = BazelJpsICReporter(span),
    )

    fsOperations.markFilesForCurrentRound(affectedByRemovedClasses.dirtyFiles.asSequence() + affectedByRemovedClasses.forceRecompileTogether)
  }

  override fun build(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
    outputConsumer: OutputConsumer
  ): ExitCode {
    val kotlinTarget = context.kotlin.targetsBinding.get(chunk.representativeTarget()) ?: return OK
    val kotlinDirtyFilesHolder = KotlinDirtySourceFilesHolder(chunk, context, dirtyFilesHolder)
    val fsOperations = BazelKotlinFsOperationsHelper(
      context = context,
      chunk = chunk,
      dirtyFilesHolder = kotlinDirtyFilesHolder,
      dataManager = dataManager,
      span = span,
    )
    val proposedExitCode = doBuild(
      chunk = chunk,
      representativeTarget = kotlinTarget,
      context = context,
      kotlinDirtyFilesHolder = kotlinDirtyFilesHolder,
      messageCollector = MessageCollectorAdapter(context, kotlinTarget),
      outputConsumer = outputConsumer,
      fsOperations = fsOperations
    )

    val actualExitCode = if (proposedExitCode == OK && fsOperations.hasMarkedDirty) ADDITIONAL_PASS_REQUIRED else proposedExitCode
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
      return NOTHING_DONE
    }

    val targets = chunk.targets

    val isChunkRebuilding = JavaBuilderUtil.isForcedRecompilationAllJavaModules(context) ||
      targets.any { kotlinContext.rebuildAfterCacheVersionChanged[it] == true }

    if (!kotlinDirtyFilesHolder.hasDirtyOrRemovedFiles) {
      if (isChunkRebuilding) {
        for (target in targets) {
          kotlinContext.hasKotlinMarker.set(target, false)
        }
      }

      targets.forEach { kotlinContext.rebuildAfterCacheVersionChanged.clean(it) }
      return NOTHING_DONE
    }

    // Request CHUNK_REBUILD when IC is off and there are dirty Kotlin files
    // Otherwise unexpected compile error might happen, when there are Groovy files,
    // but they are not dirty, so Groovy builder does not generate source stubs,
    // and Kotlin builder is filtering out output directory from classpath
    // (because it may contain outdated Java classes).
    if (!isChunkRebuilding && !representativeTarget.isIncrementalCompilationEnabled) {
      targets.forEach { kotlinContext.rebuildAfterCacheVersionChanged[it] = true }
      return CHUNK_REBUILD_REQUIRED
    }

    val targetsWithoutOutputDir = targets.filter { it.outputDir == null }
    if (targetsWithoutOutputDir.isNotEmpty()) {
      throw RuntimeException("Output directory not specified for ${targetsWithoutOutputDir.joinToString()}")
    }

    val project = context.projectDescriptor.project
    val lookupTracker = getLookupTracker(project, representativeTarget)
    val exceptActualTracker = ExpectActualTrackerImpl()
    val incrementalCaches = kotlinChunk.loadCaches()
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
    ) ?: return ABORT

    val outputItemCollector = doCompileModuleChunk(
      kotlinChunk = kotlinChunk,
      representativeTarget = representativeTarget,
      commonArguments = kotlinChunk.compilerArguments,
      context = context,
      dirtyFilesHolder = kotlinDirtyFilesHolder,
      fsOperations = fsOperations,
      environment = environment,
      incrementalCaches = incrementalCaches,
    )

    if (outputItemCollector == null) {
      return NOTHING_DONE
    }

    val compilationErrors = Utils.ERRORS_DETECTED_KEY.get(context, false)
    if (compilationErrors) {
      JavaBuilderUtil.registerFilesWithErrors(context, messageCollector.filesWithErrors.map(::File))
      return ABORT
    }
    else {
      JavaBuilderUtil.registerSuccessfullyCompiled(context, kotlinDirtyFilesHolder.allDirtyFiles)
    }

    val generatedFiles = getGeneratedFiles(context, chunk, environment.outputItemsCollector)

    if (!isKotlinBuilderInDumbMode) {
      markDirtyComplementaryMultifileClasses(
        generatedFiles = generatedFiles,
        kotlinContext = kotlinContext,
        incrementalCaches = incrementalCaches,
        fsOperations = fsOperations
      )
    }

    val kotlinTargets = kotlinContext.targetsBinding
    for ((target, outputItems) in generatedFiles) {
      val kotlinTarget = kotlinTargets.get(target) ?: error("Could not find Kotlin target for JPS target $target")
      kotlinTarget.registerOutputItems(outputConsumer, outputItems)
    }
    kotlinChunk.saveVersions()

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

    if (!representativeTarget.isIncrementalCompilationEnabled) {
      return OK
    }

    context.checkCanceled()

    environment.withProgressReporter { progress ->
      progress.progress("performing incremental compilation analysis")

      val changesCollector = ChangesCollector()

      for ((target, files) in generatedFiles) {
        val kotlinModuleBuilderTarget = kotlinContext.targetsBinding[target]!!
        kotlinModuleBuilderTarget.updateCaches(
          dirtyFilesHolder = kotlinDirtyFilesHolder,
          jpsIncrementalCache = incrementalCaches.get(kotlinModuleBuilderTarget)!!,
          files = files,
          changesCollector = changesCollector,
          environment = environment,
        )
      }

      if (!isKotlinBuilderInDumbMode || enableLookupStorageFillingInDumbMode) {
        updateLookupStorage(lookupTracker, kotlinContext.lookupStorageManager, kotlinDirtyFilesHolder)
      }

      if (!isKotlinBuilderInDumbMode && !isChunkRebuilding) {
        processChangesUsingLookups(
          collector = changesCollector,
          compiledFiles = kotlinDirtyFilesHolder.allDirtyFiles,
          lookupStorageManager = kotlinContext.lookupStorageManager,
          fsOperations = fsOperations,
          caches = incrementalCaches.values,
          reporter = BazelJpsICReporter(span),
        )
      }
    }

    return OK
  }

  // todo(1.2.80): got rid of ModuleChunk (replace with KotlinChunk)
  // todo(1.2.80): introduce KotlinRoundCompileContext, move dirtyFilesHolder, fsOperations, environment to it
  private fun doCompileModuleChunk(
    kotlinChunk: KotlinChunk,
    representativeTarget: KotlinModuleBuildTarget<*>,
    commonArguments: CommonCompilerArguments,
    context: CompileContext,
    dirtyFilesHolder: KotlinDirtySourceFilesHolder,
    fsOperations: BazelKotlinFsOperationsHelper,
    environment: JpsCompilerEnvironment,
    incrementalCaches: Map<KotlinModuleBuildTarget<*>, JpsIncrementalCache>,
  ): OutputItemsCollector? {
    kotlinChunk.targets.forEach {
      it.nextRound(context)
    }

    if (representativeTarget.isIncrementalCompilationEnabled) {
      for (target in kotlinChunk.targets) {
        val cache = incrementalCaches[target]
        val jpsTarget = target.jpsModuleBuildTarget

        val targetDirtyFiles = dirtyFilesHolder.byTarget[jpsTarget]
        if (cache != null && targetDirtyFiles != null) {
          val complementaryFiles = cache.getComplementaryFilesRecursive(targetDirtyFiles.dirty.keys + targetDirtyFiles.removed)
          context.testingContext?.buildLogger?.markedAsComplementaryFiles(ArrayList(complementaryFiles))
          fsOperations.markFilesForCurrentRound(jpsTarget, complementaryFiles)

          cache.markDirty(targetDirtyFiles.dirty.keys + targetDirtyFiles.removed)
        }
      }
    }

    registerFilesToCompile(dirtyFilesHolder, context)
    val isDoneSomething = representativeTarget.compileModuleChunk(commonArguments, dirtyFilesHolder, environment, null)
    return if (isDoneSomething) environment.outputItemsCollector else null
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
    val compilerServices = with(Services.Builder()) {
      kotlinModuleBuilderTarget.makeServices(
        builder = this,
        incrementalCaches = incrementalCaches,
        lookupTracker = lookupTracker,
        exceptActualTracer = exceptActualTracer,
        inlineConstTracker = inlineConstTracker,
        enumWhenTracker = enumWhenTracker,
        importTracker = importTracker
      )
      build()
    }

    return JpsCompilerEnvironment(
      kotlinPaths = computeKotlinPathsForJpsPlugin() ?: return null,
      services = compilerServices,
      classesToLoadByParent = classesToLoadByParent,
      messageCollector = messageCollector,
      outputItemsCollector = OutputItemsCollectorImpl(),
      progressReporter = ProgressReporterImpl(context, chunk)
    )
  }

  private fun computeKotlinPathsForJpsPlugin(): KotlinPaths? {
    val jpsKotlinHome = System.getProperty(JPS_KOTLIN_HOME_PROPERTY)?.let { File(it) }
      ?: throw RuntimeException("Make sure that '$JPS_KOTLIN_HOME_PROPERTY' system property is set in JPS process")
    if (jpsKotlinHome.exists()) {
      return KotlinPathsFromHomeDir(jpsKotlinHome)
    }
    else {
      throw RuntimeException("Cannot find kotlinc home at $jpsKotlinHome")
    }
  }

  private fun getGeneratedFiles(
    context: CompileContext,
    chunk: ModuleChunk,
    outputItemCollector: OutputItemsCollectorImpl
  ): Map<ModuleBuildTarget, List<GeneratedFile>> {
    // If there's only one target, this map is empty: get() always returns null, and the representativeTarget will be used below
    val sourceToTarget = HashMap<File, ModuleBuildTarget>()
    if (chunk.targets.size > 1) {
      for (target in chunk.targets) {
        context.kotlin.targetsBinding[target]?.sourceFiles?.forEach {
          sourceToTarget[it] = target
        }
      }
    }

    val representativeTarget = chunk.representativeTarget()
    fun SimpleOutputItem.target(): ModuleBuildTarget {
      return sourceFiles.firstOrNull()?.let { sourceToTarget[it] }
        ?: chunk.targets.singleOrNull { target ->
          target.outputDir?.let { outputDir ->
            outputFile.startsWith(outputDir)
          } ?: false
        }
        ?: representativeTarget
    }

    return outputItemCollector.outputs
      .sortedBy { it.outputFile }
      .groupBy(SimpleOutputItem::target) { it.toGeneratedFile(MetadataVersion.INSTANCE) }
  }

  private fun updateLookupStorage(
    lookupTracker: LookupTracker,
    lookupStorageManager: JpsLookupStorageManager,
    dirtyFilesHolder: KotlinDirtySourceFilesHolder
  ) {
    if (lookupTracker !is LookupTrackerImpl)
      throw AssertionError("Lookup tracker is expected to be LookupTrackerImpl, got ${lookupTracker::class.java}")

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

private fun getLookupTracker(project: JpsProject, representativeTarget: KotlinModuleBuildTarget<*>): LookupTracker {
  val testLookupTracker = project.testingContext?.lookupTracker ?: LookupTracker.DO_NOTHING
  if (representativeTarget.isIncrementalCompilationEnabled) {
    return LookupTrackerImpl(testLookupTracker)
  }
  else {
    return testLookupTracker
  }
}