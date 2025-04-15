// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "UnstableApiUsage", "HardCodedStringLiteral", "ReplaceGetOrSet", "SSBasedInspection", "LoggingSimilarMessage")

package org.jetbrains.bazel.jvm.worker.java

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.currentJavaVersion
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.lang.JavaVersion
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenCustomHashSet
import kotlinx.coroutines.ensureActive
import org.jetbrains.bazel.jvm.use
import org.jetbrains.bazel.jvm.util.emptyList
import org.jetbrains.bazel.jvm.util.emptyMap
import org.jetbrains.bazel.jvm.util.slowEqualsAwareHashStrategy
import org.jetbrains.bazel.jvm.worker.core.BazelBuildTargetIndex
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.worker.core.BazelDirtyFileHolder
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.worker.core.BazelTargetBuildOutputConsumer
import org.jetbrains.bazel.jvm.worker.core.BazelTargetBuilder
import org.jetbrains.bazel.jvm.worker.core.output.OutputSink
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ProjectPaths
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaCompilingTool
import org.jetbrains.jps.cmdline.ClasspathBootstrap
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.GlobalContextKey
import org.jetbrains.jps.incremental.StopBuildException
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.java.ExternalJavacOptionsProvider
import org.jetbrains.jps.incremental.java.ModulePathSplitter
import org.jetbrains.jps.javac.CompilationPaths
import org.jetbrains.jps.javac.DiagnosticOutputConsumer
import org.jetbrains.jps.javac.ExternalJavacManagerKey
import org.jetbrains.jps.javac.JavacFileReferencesRegistrar
import org.jetbrains.jps.javac.JavacMain
import org.jetbrains.jps.javac.ModulePath
import org.jetbrains.jps.javac.OutputFileConsumer
import org.jetbrains.jps.javac.OutputFileObject
import org.jetbrains.jps.javac.PlainMessageDiagnostic
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerOptions
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.jetbrains.jps.service.JpsServiceManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.function.BiConsumer
import java.util.function.Function
import javax.tools.Diagnostic
import javax.tools.JavaFileObject
import kotlin.coroutines.coroutineContext

private const val USE_MODULE_PATH_ONLY_OPTION = "compiler.force.module.path"

private val SHOWN_NOTIFICATIONS: Key<MutableSet<String>> = GlobalContextKey.create("_shown_notifications_")
private val MODULE_PATH_SPLITTER: Key<ModulePathSplitter> = GlobalContextKey.create("_module_path_splitter_")
@Suppress("RemoveRedundantQualifierName")
private val COMPILABLE_EXTENSIONS = java.util.List.of("java")

private const val PROC_ONLY_OPTION = "-proc:only"
private const val PROC_FULL_OPTION = "-proc:full"
private const val PROC_NONE_OPTION = "-proc:none"
private const val RELEASE_OPTION = "--release"
private const val TARGET_OPTION = "-target"

@Suppress("SpellCheckingInspection")
private const val PROCESSORPATH_OPTION = "-processorpath"
private const val ENABLE_PREVIEW_OPTION = "--enable-preview"
private const val PROCESSOR_MODULE_PATH_OPTION = "--processor-module-path"
private const val SOURCE_OPTION = "-source"
private const val SYSTEM_OPTION = "--system"

@Suppress("RemoveRedundantQualifierName")
private val FILTERED_OPTIONS = java.util.Set.of(TARGET_OPTION, RELEASE_OPTION, "-d")

@Suppress("RemoveRedundantQualifierName", "SpellCheckingInspection")
private val FILTERED_SINGLE_OPTIONS: MutableSet<String?> = java.util.Set.of(
  "-g", "-deprecation", "-nowarn", "-verbose", PROC_NONE_OPTION, PROC_ONLY_OPTION, PROC_FULL_OPTION, "-proceedOnError"
)

private val USER_DEFINED_BYTECODE_TARGET = Key.create<String>("_user_defined_bytecode_target_")

private val moduleInfoFileSuffix = File.separatorChar + "module-info.java"

internal class BazelJavaBuilder(
  private val isIncremental: Boolean,
  private val isDebugEnabled: Boolean,
  private val tracer: Tracer,
  private val out: Appendable,
) : BazelTargetBuilder(BuilderCategory.TRANSLATOR) {
  private val refRegistrars = ArrayList<JavacFileReferencesRegistrar>()

  override fun getPresentableName(): String = "java"

  override fun buildStarted(context: CompileContext) {
    MODULE_PATH_SPLITTER.set(context, ModulePathSplitter(ExplodedModuleNameFinder(context)))
    SHOWN_NOTIFICATIONS.set(context, Collections.synchronizedSet(HashSet()))
    if (isIncremental) {
      JavaBackwardReferenceIndexWriter.initialize(context)
      for (registrar in JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar::class.java)) {
        if (registrar.isEnabled) {
          registrar.initialize()
          refRegistrars.add(registrar)
        }
      }
    }
  }

  override fun buildFinished(context: CompileContext) {
    refRegistrars.clear()
  }

  override fun getCompilableFileExtensions(): List<String> = COMPILABLE_EXTENSIONS

  override suspend fun build(
    context: BazelCompileContext,
    module: JpsModule,
    chunk: ModuleChunk,
    target: BazelModuleBuildTarget,
    dirtyFilesHolder: BazelDirtyFileHolder,
    outputConsumer: BazelTargetBuildOutputConsumer,
    outputSink: OutputSink,
  ): ExitCode {
    val filesToCompile: Sequence<Path> = if (isIncremental) {
      val modified = ArrayList<Path>()
      dirtyFilesHolder.processFilesToRecompile { files ->
        for (file in files) {
          if (file.toString().endsWith(".java")) {
            modified.add(file)
          }
        }
        true
      }

      modified.asSequence()
    }
    else {
      target.sources.asSequence().filter { it.toString().endsWith(".java") }
    }

    val hasSourcesToCompile = filesToCompile.any()
    if (!hasSourcesToCompile && !dirtyFilesHolder.hasRemovedFiles()) {
      return ExitCode.NOTHING_DONE
    }

    val jpsJavaFileProvider = BazelJpsJavacFileProvider(
      outputSink = outputSink,
      expectedOutputFileCount = filesToCompile.count(),
      outputConsumer = outputConsumer,
      mappingsCallback = if (isIncremental) JavaBuilderUtil.getDependenciesRegistrar(context) else null,
    )
    var filesWithErrors: Collection<File>? = null
    try {
      if (hasSourcesToCompile) {
        val classpath = module.container.getChild(BazelConfigurationHolder.KIND).classPath
        val diagnosticSink = JavaDiagnosticSink(context = context, registrars = refRegistrars, out = out)
        val compiledOk = tracer.spanBuilder("compile java files")
          .also { spanBuilder ->
            if (isDebugEnabled) {
              spanBuilder.setAttribute(AttributeKey.stringArrayKey("files"), filesToCompile.map { it.toString() }.toList())
              spanBuilder.setAttribute(AttributeKey.stringArrayKey("classpath"), classpath.map { it.toString() })
            }
          }
          .use {
            try {
              compileJava(
                context = context,
                module = module,
                chunk = chunk,
                target = target,
                files = filesToCompile,
                originalClassPath = classpath,
                diagnosticSink = diagnosticSink,
                jpsJavaFileProvider = jpsJavaFileProvider,
                span = it,
              )
            }
            finally {
              filesWithErrors = diagnosticSink.getFilesWithErrors()
            }
          }

        coroutineContext.ensureActive()

        if (!compiledOk && diagnosticSink.errorCount == 0) {
          // unexpected exception occurred or compiler did not output any errors for some reason
          diagnosticSink.report(PlainMessageDiagnostic(Diagnostic.Kind.ERROR, "Compilation failed: internal java compiler error"))
        }

        if (diagnosticSink.errorCount > 0 && !Utils.PROCEED_ON_ERROR_KEY.get(context, false)) {
          throw StopBuildException("Compilation failed: errors: ${diagnosticSink.errorCount}; warnings: ${diagnosticSink.warningCount}")
        }
      }
    }
    finally {
      JavaBuilderUtil.registerFilesToCompile(context, filesToCompile.map { it.toFile() }.toList())
      if (filesWithErrors != null) {
        JavaBuilderUtil.registerFilesWithErrors(context, filesWithErrors)
      }
      jpsJavaFileProvider.registerOutputs(context)
    }
    return if (hasSourcesToCompile) ExitCode.OK else ExitCode.NOTHING_DONE
  }

  private fun compileJava(
    context: CompileContext,
    span: Span,
    target: BazelModuleBuildTarget,
    module: JpsModule,
    chunk: ModuleChunk,
    files: Sequence<Path>,
    originalClassPath: Array<Path>,
    diagnosticSink: DiagnosticOutputConsumer,
    jpsJavaFileProvider: BazelJpsJavacFileProvider,
  ): Boolean {
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    val targetLanguageLevel = javaExtensionService.getModuleExtension(module)!!.languageLevel!!.feature()
    // when we use a forked external javac, compilers from SDK 1.7 and higher are supported
    val forkSdk = if (isTargetReleaseSupported(currentJavaVersion().feature, targetLanguageLevel)) {
      null
    }
    else {
      getForkedJavacSdk(diagnostic = diagnosticSink, module = module, targetLanguageLevel = targetLanguageLevel, span = span) ?: return false
    }

    val compilerSdkVersion = forkSdk?.second ?: currentJavaVersion().feature
    val compilingTool = JavacCompilerTool()
    val vmCompilerOptions = getCompilationOptions(
      compilerSdkVersion = compilerSdkVersion,
      context = context,
      module = module,
      profile = javaExtensionService.getCompilerConfiguration(context.projectDescriptor.project).getAnnotationProcessingProfile(module),
      compilingTool = compilingTool,
    )
    val options = vmCompilerOptions.second
    var moduleInfoFile: Path? = null
    if (targetLanguageLevel >= 9) {
      for (file in target.sources) {
        if (file.toString().endsWith(moduleInfoFileSuffix)) {
          moduleInfoFile = file
          break
        }
      }
    }

    val classPath: Sequence<Path>?
    val modulePath: ModulePath
    if (moduleInfoFile == null) {
      modulePath = ModulePath.EMPTY
      classPath = originalClassPath.asSequence()
    }
    else {
      // has modules
      val splitter = MODULE_PATH_SPLITTER.get(context)
      val pair = splitter.splitPath(
        moduleInfoFile.toFile(), emptySet(), ProjectPaths.getCompilationModulePath(chunk, false), collectAdditionalRequires(options)
      )
      val useModulePathOnly = System.getProperty(USE_MODULE_PATH_ONLY_OPTION).toBoolean() /*compilerConfig.useModulePathOnly()*/
      if (useModulePathOnly) {
        // in Java 9, named modules are not allowed to read classes from the classpath
        // moreover, the compiler requires all transitive dependencies to be on the module path
        val mpBuilder = ModulePath.newBuilder()
        for (file in ProjectPaths.getCompilationModulePath(chunk, false)) {
          mpBuilder.add(pair.first.getModuleName(file), file)
        }
        modulePath = mpBuilder.create()
        classPath = emptySequence()
      }
      else {
        // placing only explicitly referenced modules into the module path and the rest of dependencies to classpath
        modulePath = pair.first
        classPath = pair.second.asSequence().map { it.toPath() }
      }
    }

    if (forkSdk != null) {
      val server = ensureJavacServerStarted(context)
      val paths = CompilationPaths.create(
        /* platformCp = */ emptyList(),
        /* cp = */ classPath.map { it.toFile() }.toList(),
        /* upgradeModCp = */ emptyList(),
        /* modulePath = */ modulePath,
        /* sourcePath = */ emptyList(),
      )
      val heapSize = Utils.suggestForkedCompilerHeapSize()
      return invokeJavac(
        compilerSdkVersion = compilerSdkVersion,
        context = context,
        module = module,
        compilingTool = compilingTool,
        options = options,
        files = files,
        outSink = {
          throw IllegalStateException("should not be called")
        },
      ) { options, files, outSink ->
        logJavacCall(options = options, mode = "fork", span = span)
        server.forkJavac(
          forkSdk.first,
          heapSize,
          vmCompilerOptions.first,
          options,
          paths,
          files,
          emptyMap(),
          diagnosticSink,
          outSink,
          compilingTool,
          context.cancelStatus,
          true,
        ).get()
      }
    }

    return invokeJavac(
      compilerSdkVersion = compilerSdkVersion,
      context = context,
      module = module,
      compilingTool = compilingTool,
      options = options,
      files = files,
      outSink = {
        throw IllegalStateException("should not be called")
      },
      javacCall = { options, files, outSink ->
        logJavacCall(options = options, mode = "in-process", span = span)
        JavacMain.compile(
          /* options = */ options,
          /* sources = */ files,
          /* classpath = */ classPath.map { it.toFile() }.toList(),
          /* platformClasspath = */ emptyList(),
          /* modulePath = */ modulePath,
          /* upgradeModulePath = */ emptyList(),
          /* sourcePath = */ emptyList(),
          /* outputDirToRoots = */ emptyMap(),
          /* diagnosticConsumer = */ diagnosticSink,
          /* outputSink = */ outSink,
          /* canceledStatus = */ context.cancelStatus,
          /* compilingTool = */ compilingTool,
          /* jpsJavacFileProvider = */ jpsJavaFileProvider,
        )
      }
    )
  }

  override fun chunkBuildFinished(context: CompileContext?, chunk: ModuleChunk?) {
    JavaBuilderUtil.cleanupChunkResources(context)
    ExternalJavacManagerKey.KEY.get(context)?.shutdownIdleProcesses()
  }

  override fun getExpectedBuildTime(): Long = 100
}

private class ExplodedModuleNameFinder(context: CompileContext) : Function<File?, String?> {
  private val moduleTarget = (context.projectDescriptor.buildTargetIndex as BazelBuildTargetIndex).moduleTarget

  override fun apply(outputDir: File?): String? = moduleTarget.module.name.trim()
}

private fun logJavacCall(options: Iterable<String>, mode: String, span: Span) {
  if (span.isRecording) {
    span.addEvent("compiling chunk mode", Attributes.of(
      AttributeKey.stringArrayKey("options"), options.toList(),
      AttributeKey.stringKey("mode"), mode,
    ))
  }
}

private fun collectAdditionalRequires(options: Iterable<String>): Collection<String> {
  // --add-reads module=other-module(,other-module)*
  // The option specifies additional modules to be considered as required by a given module.
  val result = ObjectLinkedOpenCustomHashSet<String>(slowEqualsAwareHashStrategy())
  val it = options.iterator()
  while (it.hasNext()) {
    val option = it.next()
    if ("--add-reads".equals(option, ignoreCase = true) && it.hasNext()) {
      val moduleNames = it.next().substringAfter('=')
      if (moduleNames.isNotEmpty()) {
        result.addAll(moduleNames.splitToSequence(','))
      }
    }
  }
  return result
}

internal fun getAssociatedSdk(module: JpsModule): Pair<JpsSdk<JpsDummyElement?>, Int>? {
  // assuming all modules in the chunk have the same associated JDK,
  // this constraint should be validated on build start
  val sdk = module.getSdk(JpsJavaSdkType.INSTANCE) ?: return null
  return Pair(sdk, JpsJavaSdkType.getJavaVersion(sdk))
}

internal fun isTargetReleaseSupported(compilerVersion: Int, targetPlatformVersion: Int): Boolean {
  if (targetPlatformVersion > compilerVersion) {
    return false
  }
  else {
    return when {
      compilerVersion < 9 -> true
      compilerVersion <= 11 -> targetPlatformVersion >= 6
      compilerVersion <= 19 -> targetPlatformVersion >= 7
      else -> targetPlatformVersion >= 8
    }
  }
}

private fun invokeJavac(
  compilerSdkVersion: Int,
  context: CompileContext,
  module: JpsModule,
  compilingTool: JavaCompilingTool,
  options: Iterable<String>,
  files: Sequence<Path>,
  outSink: (OutputFileObject) -> Unit,
  javacCall: (options: Iterable<String>, files: Iterable<File>, outSink: OutputFileConsumer) -> Boolean,
): Boolean {
  if (options.contains(PROC_ONLY_OPTION)) {
    // make a dedicated javac call for annotation processing only
    val generated = ArrayList<File>()
    val processingSuccess = javacCall(options, files.map { it.toFile() }.asIterable(), OutputFileConsumer { fileObject ->
      if (fileObject.getKind() == JavaFileObject.Kind.SOURCE) {
        generated.add(fileObject.file)
      }
      outSink(fileObject)
    })
    if (!processingSuccess) {
      return false
    }

    // now call javac with processor-generated sources and without processing-related options
    val compileOnlyOptions = getCompilationOptions(
      compilerSdkVersion = compilerSdkVersion,
      context = context,
      module = module,
      profile = null,
      compilingTool = compilingTool,
    ).second
    return javacCall(compileOnlyOptions, (files.map { it.toFile() } + generated).asIterable(), outSink)
  }

  return javacCall(options, files.map { it.toFile() }.asIterable(), outSink)
}

private fun shouldUseReleaseOption(compilerVersion: Int, chunkSdkVersion: Int, targetPlatformVersion: Int): Boolean {
  // --release option is supported in java9+ and higher
  if (compilerVersion >= 9 && chunkSdkVersion > 0 && targetPlatformVersion > 0) {
    if (chunkSdkVersion < 9) {
      // target sdk is set explicitly and differs from compiler SDK, so for consistency we should link against it
      return false
    }
    // chunkSdkVersion >= 9, so we have no rt.jar anymore and '-release' is the only cross-compilation option available
    // Only specify '--release' when cross-compilation is indeed really required.
    // Otherwise, '--release' may not be compatible with other compilation options, e.g., exporting a package from system module
    return compilerVersion != targetPlatformVersion
  }
  return false
}

private fun getCompilationOptions(
  compilerSdkVersion: Int,
  context: CompileContext,
  module: JpsModule,
  profile: ProcessorConfigProfile?,
  compilingTool: JavaCompilingTool,
): Pair<Iterable<String>, Iterable<String>> {
  val compilationOptions = ArrayList<String>()
  val vmOptions = ArrayList<String>()
  if (!JavacMain.TRACK_AP_GENERATED_DEPENDENCIES) {
    vmOptions.add("-D" + JavacMain.TRACK_AP_GENERATED_DEPENDENCIES_PROPERTY + "=false")
  }
  if (compilerSdkVersion > 15) {
    // enable javac-related reflection tricks in JPS
    ClasspathBootstrap.configureReflectionOpenPackages { vmOptions.add(it) }
  }
  val project = context.projectDescriptor.project
  val compilerOptions = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).currentCompilerOptions
  if (compilerOptions.DEBUGGING_INFO) {
    compilationOptions.add("-g")
  }
  if (compilerOptions.DEPRECATION) {
    compilationOptions.add("-deprecation")
  }
  if (compilerOptions.GENERATE_NO_WARNINGS) {
    @Suppress("SpellCheckingInspection")
    compilationOptions.add("-nowarn")
  }

  val customArgs = compilerOptions.ADDITIONAL_OPTIONS_STRING
  require(compilerOptions.ADDITIONAL_OPTIONS_OVERRIDE.isEmpty())

  if (customArgs != null && !customArgs.isEmpty()) {
    var appender = BiConsumer { obj: MutableList<String>, e: String -> obj.add(e) }
    val baseDirectory = JpsModelSerializationDataService.getBaseDirectory(module)
    if (baseDirectory != null) {
      //this is a temporary workaround to allow passing per-module compiler options for Eclipse compiler in form
      // `-properties $MODULE_DIR$/.settings/org.eclipse.jdt.core.prefs`
      val moduleDirPath = baseDirectory.toPath().toAbsolutePath().normalize().toString()
      appender = BiConsumer { strings, option -> strings.add(option.replace(PathMacroUtil.DEPRECATED_MODULE_DIR, moduleDirPath)) }
    }

    var skip = false
    var targetOptionFound = false
    for (userOption in ParametersListUtil.parse(customArgs)) {
      if (FILTERED_OPTIONS.contains(userOption)) {
        skip = true
        targetOptionFound = TARGET_OPTION == userOption
        continue
      }
      if (skip) {
        skip = false
        if (targetOptionFound) {
          targetOptionFound = false
          USER_DEFINED_BYTECODE_TARGET.set(context, userOption)
        }
      }
      else {
        if (!FILTERED_SINGLE_OPTIONS.contains(userOption)) {
          if (userOption.startsWith("-J-")) {
            vmOptions.add(userOption.substring("-J".length))
          }
          else {
            appender.accept(compilationOptions, userOption)
          }
        }
      }
    }
  }

  for (extension in JpsServiceManager.getInstance().getExtensions(ExternalJavacOptionsProvider::class.java)) {
    vmOptions.addAll(extension.getOptions(compilingTool, compilerSdkVersion))
  }

  addCompilationOptions(
    compilerOptions = compilerOptions,
    compilerSdkVersion = compilerSdkVersion,
    options = compilationOptions,
    module = module,
    profile = profile,
  )

  return Pair(vmOptions, compilationOptions)
}

private fun addCompilationOptions(
  compilerSdkVersion: Int,
  options: MutableList<String>,
  module: JpsModule,
  profile: ProcessorConfigProfile?,
  compilerOptions: JpsJavaCompilerOptions,
) {
  addCrossCompilationOptions(compilerSdkVersion, options, module, compilerOptions)

  if (!options.contains(ENABLE_PREVIEW_OPTION)) {
    val level = JpsJavaExtensionService.getInstance().getLanguageLevel(module)
    if (level != null && level.isPreview) {
      options.add(ENABLE_PREVIEW_OPTION)
    }
  }

  if (addAnnotationProcessingOptions(options, profile)) {
    checkNotNull(profile)

    // by default required for javac from version 23
    if (profile.isProcOnly) {
      options.add(PROC_ONLY_OPTION)
    }
    else if (compilerSdkVersion > 22) {
      options.add(PROC_FULL_OPTION)
    }

    val sourceOutput = ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(module, false, profile)
    if (sourceOutput != null) {
      Files.createDirectories(sourceOutput.toPath())
      options.add("-s")
      options.add(sourceOutput.path)
    }
  }
}

/**
 * @return true if annotation processing is enabled and corresponding options were added, false if the profile is null or disabled
 */
private fun addAnnotationProcessingOptions(options: MutableList<String>, profile: AnnotationProcessingConfiguration?): Boolean {
  if (profile == null || !profile.isEnabled) {
    options.add(PROC_NONE_OPTION)
    return false
  }

  // configuring annotation processing
  if (!profile.isObtainProcessorsFromClasspath) {
    val processorsPath = profile.processorPath.trim()
    options.add(if (profile.isUseProcessorModulePath) PROCESSOR_MODULE_PATH_OPTION else PROCESSORPATH_OPTION)
    options.add(FileUtilRt.toSystemDependentName(processorsPath))
  }

  val processors = profile.processors
  if (!processors.isEmpty()) {
    options.add("-processor")
    options.add(processors.joinToString(separator = ","))
  }

  for (optionEntry in profile.processorOptions.entries) {
    options.add("-A${optionEntry.key}=${optionEntry.value}")
  }
  return true
}

private fun addCrossCompilationOptions(compilerSdkVersion: Int, options: MutableList<String>, module: JpsModule, compilerOptions: JpsJavaCompilerOptions) {
  val level = requireNotNull(JpsJavaExtensionService.getInstance().getLanguageLevel(module)) {
    "Language level must be set for module ${module.name}"
  }
  if (level.isPreview) {
    options.add(ENABLE_PREVIEW_OPTION)
  }

  require(level != LanguageLevel.JDK_X)

  val bytecodeTarget = level.feature()
  require(bytecodeTarget > 0)

  // release cannot be used if add-exports specified
  if (compilerOptions.ADDITIONAL_OPTIONS_STRING == null && shouldUseReleaseOption(compilerSdkVersion, bytecodeTarget, bytecodeTarget)) {
    options.add(RELEASE_OPTION)
    options.add(complianceOption(bytecodeTarget))
    return
  }

  val languageLevel = level.feature()
  // alternatively, using `-source`, `-target` and `--system` (or `-bootclasspath`) options
  if (languageLevel > 0 && !options.contains(SOURCE_OPTION)) {
    options.add(SOURCE_OPTION)
    options.add(complianceOption(languageLevel))
  }

  options.add(TARGET_OPTION)
  options.add(complianceOption(bytecodeTarget))

  if (compilerSdkVersion >= 9) {
    val associatedSdk = getAssociatedSdk(module)
    if (associatedSdk != null && associatedSdk.second >= 9 && associatedSdk.second != compilerSdkVersion) {
      val homePath = associatedSdk.first.homePath
      if (homePath != null) {
        options.add(SYSTEM_OPTION)
        options.add(FileUtilRt.toSystemIndependentName(homePath))
      }
    }
  }
}

private fun complianceOption(major: Int): String = JpsJavaSdkType.complianceOption(JavaVersion.compose(major))

