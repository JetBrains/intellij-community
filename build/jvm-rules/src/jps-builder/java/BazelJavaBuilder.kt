// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "UnstableApiUsage", "HardCodedStringLiteral", "ReplaceGetOrSet", "SSBasedInspection", "LoggingSimilarMessage")

package org.jetbrains.bazel.jvm.jps.java

import com.intellij.compiler.instrumentation.FailSafeClassReader
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.lang.JavaVersion
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.bazel.jvm.jps.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.jps.OutputSink
import org.jetbrains.bazel.jvm.jps.hashSet
import org.jetbrains.bazel.jvm.jps.impl.BazelBuildRootIndex
import org.jetbrains.bazel.jvm.jps.impl.BazelDirtyFileHolder
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.BazelTargetBuildOutputConsumer
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ProjectPaths
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
import org.jetbrains.jps.builders.DirtyFilesHolder
import org.jetbrains.jps.builders.FileProcessor
import org.jetbrains.jps.builders.JpsBuildBundle
import org.jetbrains.jps.builders.ModuleBasedTarget
import org.jetbrains.jps.builders.TargetOutputIndex
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase
import org.jetbrains.jps.builders.impl.TargetOutputIndexImpl
import org.jetbrains.jps.builders.impl.java.JavacCompilerTool
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.builders.java.JavaCompilingTool
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType
import org.jetbrains.jps.builders.java.JavaSourceRootDescriptor
import org.jetbrains.jps.builders.java.dependencyView.Callbacks.Backend
import org.jetbrains.jps.cmdline.ClasspathBootstrap
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.CompiledClass
import org.jetbrains.jps.incremental.GlobalContextKey
import org.jetbrains.jps.incremental.ModuleBuildTarget
import org.jetbrains.jps.incremental.ModuleLevelBuilder
import org.jetbrains.jps.incremental.StopBuildException
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.java.CustomOutputDataListener
import org.jetbrains.jps.incremental.java.ExternalJavacOptionsProvider
import org.jetbrains.jps.incremental.java.ModulePathSplitter
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.javac.CompilationPaths
import org.jetbrains.jps.javac.DiagnosticOutputConsumer
import org.jetbrains.jps.javac.ExternalJavacManager
import org.jetbrains.jps.javac.ExternalJavacManagerKey
import org.jetbrains.jps.javac.JavacFileReferencesRegistrar
import org.jetbrains.jps.javac.JavacMain
import org.jetbrains.jps.javac.JpsInfoDiagnostic
import org.jetbrains.jps.javac.JpsJavacFileProvider
import org.jetbrains.jps.javac.ModulePath
import org.jetbrains.jps.javac.OutputFileConsumer
import org.jetbrains.jps.javac.OutputFileObject
import org.jetbrains.jps.javac.PlainMessageDiagnostic
import org.jetbrains.jps.javac.ast.api.JavacFileData
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.jetbrains.jps.service.JpsServiceManager
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer
import java.util.function.Function
import javax.lang.model.element.Modifier
import javax.lang.model.element.NestingKind
import javax.tools.Diagnostic
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.StandardLocation
import kotlin.io.path.invariantSeparatorsPathString

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

@Suppress("RemoveRedundantQualifierName", "SpellCheckingInspection")
private val POSSIBLY_CONFLICTING_OPTIONS = java.util.Set.of(
  SOURCE_OPTION, SYSTEM_OPTION, "--boot-class-path", "-bootclasspath", "--class-path", "-classpath", "-cp", PROCESSORPATH_OPTION, "-sourcepath", "--module-path", "-p", "--module-source-path"
)

private val USER_DEFINED_BYTECODE_TARGET = Key.create<String>("_user_defined_bytecode_target_")

private val moduleInfoFileSuffix = File.separatorChar + "module-info.java"

internal class BazelJavaBuilder(
  private val isIncremental: Boolean,
  private val span: Span,
  private val out: Appendable,
) : ModuleLevelBuilder(BuilderCategory.TRANSLATOR) {
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

  override fun chunkBuildStarted(context: CompileContext, chunk: ModuleChunk) {
    if (!isIncremental) {
      return
    }

    // before the first compilation round starts: find and mark dirty all classes that depend on removed or moved classes so
    // that all such files are compiled in the first round.
    JavaBuilderUtil.markDirtyDependenciesForInitialRound(context, object : DirtyFilesHolderBase<JavaSourceRootDescriptor, ModuleBuildTarget>(context) {
      override fun processDirtyFiles(processor: FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>) {
        context.projectDescriptor.fsState.processFilesToRecompile(context, chunk.targets.single(), processor)
      }
    }, chunk)
  }

  override fun buildFinished(context: CompileContext) {
    refRegistrars.clear()
  }

  override fun getCompilableFileExtensions(): List<String> = COMPILABLE_EXTENSIONS

  override fun build(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
    outputConsumer: OutputConsumer,
  ): ExitCode? {
    throw IllegalStateException("Should not be called")
  }

  fun build(
    context: CompileContext,
    chunk: ModuleChunk,
    target: BazelModuleBuildTarget,
    dirtyFilesHolder: BazelDirtyFileHolder,
    outputConsumer: BazelTargetBuildOutputConsumer,
    outputSink: OutputSink,
  ): ExitCode? {
    val filesToCompile: Sequence<Path> = if (isIncremental) {
      val modified = ArrayList<Path>()
      dirtyFilesHolder.processFilesToRecompile { file ->
        if (file.toString().endsWith(".java")) {
          modified.add(file)
        }
        true
      }

      if (!modified.isEmpty() && span.isRecording) {
        span.addEvent("Compiling files", Attributes.of(AttributeKey.stringArrayKey("filesToCompile"), modified.map { it.toString() }))
      }

      modified.asSequence()
    }
    else {
      target.sources.asSequence().filter { it.toString().endsWith(".java") }
    }

    var moduleInfoFile: Path? = null
    if ((dirtyFilesHolder.hasRemovedFiles() || filesToCompile.any()) &&
      getTargetPlatformLanguageVersion(chunk.representativeTarget().module) >= 9) {
      for (file in target.sources) {
        if (file.toString().endsWith(moduleInfoFileSuffix)) {
          moduleInfoFile = file
          break
        }
      }
    }

    return compile(
      context = context,
      chunk = chunk,
      dirtyFilesHolder = dirtyFilesHolder,
      files = filesToCompile,
      outputConsumer = outputConsumer,
      moduleInfoFile = moduleInfoFile,
      outputJar = outputSink,
    )
  }

  private fun compile(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
    files: Sequence<Path>,
    outputConsumer: BazelTargetBuildOutputConsumer,
    outputJar: OutputSink,
    moduleInfoFile: Path?,
  ): ExitCode {
    val hasSourcesToCompile = files.any()
    if (!hasSourcesToCompile && !dirtyFilesHolder.hasRemovedFiles()) {
      return ExitCode.NOTHING_DONE
    }

    JavaBuilderUtil.ensureModuleHasJdk(chunk.representativeTarget().module, context, "java")
    val classpath = chunk.modules.single().container.getChild(BazelConfigurationHolder.KIND).javacClassPath

    // begin compilation round
    val outputSink = JavacOutputFileSink(
      context = context,
      outputConsumer = outputConsumer,
      mappingsCallback = if (isIncremental) JavaBuilderUtil.getDependenciesRegistrar(context) else null,
      outputSink = outputJar,
    )
    var filesWithErrors: Collection<File>? = null
    try {
      if (hasSourcesToCompile) {
        val diagnosticSink = DiagnosticSink(context = context, registrars = refRegistrars, out = out)
        val chunkName = chunk.name

        var compiledOk: Boolean
        if (span.isRecording) {
          span.addEvent(
            "compiling java files",
            Attributes.of(
              AttributeKey.stringKey("module.name"), chunkName,
              AttributeKey.stringArrayKey("files"), files.map { it.toString() }.toList(),
              AttributeKey.stringArrayKey("classpath"), classpath.map { it.toString() },
            )
          )
        }

        try {
          compiledOk = compileJava(
            context = context,
            chunk = chunk,
            files = files,
            originalClassPath = classpath,
            sourcePath = emptyList(),
            diagnosticSink = diagnosticSink,
            outputSink = outputSink,
            moduleInfoFile = moduleInfoFile,
            outputJar = outputJar,
          )
        }
        finally {
          filesWithErrors = diagnosticSink.filesWithErrors
        }

        context.checkCanceled()

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
      JavaBuilderUtil.registerFilesToCompile(context, files.map { it.toFile() }.toList())
      if (filesWithErrors != null) {
        JavaBuilderUtil.registerFilesWithErrors(context, filesWithErrors)
      }
      JavaBuilderUtil.registerSuccessfullyCompiled(context, outputSink.successfullyCompiled)
    }
    return if (hasSourcesToCompile) ExitCode.OK else ExitCode.NOTHING_DONE
  }

  private fun compileJava(
    context: CompileContext,
    chunk: ModuleChunk,
    files: Sequence<Path>,
    originalClassPath: Array<Path>,
    sourcePath: Collection<File>,
    diagnosticSink: DiagnosticOutputConsumer,
    outputSink: JavacOutputFileSink,
    moduleInfoFile: Path?,
    outputJar: OutputSink,
  ): Boolean {
    val javaExtensionService = JpsJavaExtensionService.getInstance()
    val targetLanguageLevel = javaExtensionService.getModuleExtension(chunk.representativeTarget().module)!!.languageLevel!!.feature()
    // when we use a forked external javac, compilers from SDK 1.7 and higher are supported
    val forkSdk = if (isTargetReleaseSupported(JavaVersion.current().feature, targetLanguageLevel)) {
      null
    }
    else {
      getForkedJavacSdk(diagnostic = diagnosticSink, chunk = chunk, targetLanguageLevel = targetLanguageLevel, span = span) ?: return false
    }

    val compilerSdkVersion = forkSdk?.second ?: JavaVersion.current().feature
    val compilingTool = JavacCompilerTool()
    val vmCompilerOptions = getCompilationOptions(
      compilerSdkVersion = compilerSdkVersion,
      context = context,
      chunk = chunk,
      profile = javaExtensionService.getCompilerConfiguration(context.projectDescriptor.project).getAnnotationProcessingProfile(chunk.modules.single()),
      compilingTool = compilingTool,
    )
    val options = vmCompilerOptions.second
    val outs = buildOutputDirectoriesMap(chunk)

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
        moduleInfoFile.toFile(), outs.keys, ProjectPaths.getCompilationModulePath(chunk, false), collectAdditionalRequires(options)
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
        emptyList(),
        classPath.map { it.toFile() }.toList(),
        emptyList(),
        modulePath,
        sourcePath
      )
      val heapSize = Utils.suggestForkedCompilerHeapSize()
      return invokeJavac(
        compilerSdkVersion = compilerSdkVersion,
        context = context,
        chunk = chunk,
        compilingTool = compilingTool,
        options = options,
        files = files,
        outSink = {
          outputSink.save(fileObject = it)
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
          outs,
          diagnosticSink,
          outSink,
          compilingTool,
          context.cancelStatus,
          true,
        ).get()
      }
    }

    val jpsJavaFileProvider = object : JpsJavacFileProvider {
      override fun list(
        location: JavaFileManager.Location,
        packageName: String,
        kinds: Set<JavaFileObject.Kind>,
        recurse: Boolean
      ): Iterable<JavaFileObject> {
        if (!kinds.contains(JavaFileObject.Kind.CLASS)) {
          return emptySequence<JavaFileObject>().asIterable()
        }

        return sequence {
          outputJar.findByPackage(packageName, recurse) { relativePath, data, offset, length ->
            yield(InMemoryJavaFileObject(path = relativePath, data = data, offset = offset, length = length))
          }
        }.asIterable()
      }

      override fun inferBinaryName(location: JavaFileManager.Location, file: JavaFileObject): String? {
        if (location == StandardLocation.CLASS_PATH && file is InMemoryJavaFileObject) {
          return file.path.substringBeforeLast('.').replace('/', '.')
        }
        return null
      }
    }

    return invokeJavac(
      compilerSdkVersion = compilerSdkVersion,
      context = context,
      chunk = chunk,
      compilingTool = compilingTool,
      options = options,
      files = files,
      outSink = {
        outputSink.save(fileObject = it)
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
          /* sourcePath = */ sourcePath,
          /* outputDirToRoots = */ outs,
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

private class InMemoryJavaFileObject(
  @JvmField val path: String,
  private val data: ByteArray,
  private val offset: Int,
  private val length: Int,
) : JavaFileObject {
  override fun getKind(): JavaFileObject.Kind = JavaFileObject.Kind.CLASS

  override fun isNameCompatible(simpleName: String, kind: JavaFileObject.Kind): Boolean {
    return kind == JavaFileObject.Kind.CLASS && simpleName == path
  }

  override fun getNestingKind(): NestingKind? = null

  override fun getAccessLevel(): Modifier? = null

  override fun toUri(): URI? {
    throw UnsupportedOperationException()
  }

  override fun getName(): String = path

  override fun openInputStream(): InputStream = ByteArrayInputStream(data, offset, length)

  override fun openOutputStream(): OutputStream = throw IllegalStateException()

  override fun openReader(ignoreEncodingErrors: Boolean): Reader = getCharContent(true).reader()

  override fun getCharContent(ignoreEncodingErrors: Boolean): String {
    return data.decodeToString(startIndex = offset, endIndex = offset + length)
  }

  override fun openWriter(): Writer = throw IllegalStateException()

  override fun getLastModified(): Long = 1

  override fun delete(): Boolean = throw IllegalStateException()
}

private class ExplodedModuleNameFinder(context: CompileContext) : Function<File?, String?> {
  private val outsIndex: TargetOutputIndex

  init {
    val targetIndex = context.projectDescriptor.buildTargetIndex
    val javaModuleTargets = ArrayList<ModuleBuildTarget?>()
    for (type in JavaModuleBuildTargetType.ALL_TYPES) {
      javaModuleTargets.addAll(targetIndex.getAllTargets(type))
    }
    outsIndex = TargetOutputIndexImpl(javaModuleTargets, context)
  }

  override fun apply(outputDir: File?): String? {
    for (target in outsIndex.getTargetsByOutputFile(outputDir!!)) {
      if (target is ModuleBasedTarget<*>) {
        return target.module.name.trim()
      }
    }
    return ModulePathSplitter.DEFAULT_MODULE_NAME_SEARCH.apply(outputDir)
  }
}

private class JavacOutputFileSink(
  private val context: CompileContext,
  private val outputConsumer: BazelTargetBuildOutputConsumer,
  private val mappingsCallback: Backend?,
  private val outputSink: OutputSink,
) {
  @JvmField
  val successfullyCompiled = hashSet<File>()

  fun save(fileObject: OutputFileObject) {
    val content = fileObject.content
    val file = fileObject.file
    if (content == null) {
      context.processMessage(CompilerMessage("java", BuildMessage.Kind.WARNING, "Missing content for file ${file.path}"))
    }
    else {
      outputSink.registerJavacOutput(fileObject.relativePath, content.buffer, content.offset, content.length)
    }

    val outKind = fileObject.getKind()
    val sourceIoFiles = fileObject.sourceFiles.toList()
    val sourceFiles = sourceIoFiles.map { it.toPath() }
    if (!sourceFiles.isEmpty() && content != null) {
      val sourcePaths = sourceFiles.map { it.invariantSeparatorsPathString }
      var rootDescriptor: JavaSourceRootDescriptor? = null
      for (sourceFile in sourceFiles) {
        rootDescriptor = (context.projectDescriptor.buildRootIndex as BazelBuildRootIndex).fileToDescriptors.get(sourceFile)
        if (rootDescriptor != null) {
          break
        }
      }

      if (rootDescriptor == null) {
        // was not able to determine the source root descriptor, or the source root is excluded from compilation (e.g., for annotation processors)
        if (outKind == JavaFileObject.Kind.CLASS) {
          outputConsumer.registerCompiledClass(null, CompiledClass(fileObject.file, sourceIoFiles, fileObject.className, content))
        }
      }
      else {
        // first, handle [src->output] mapping and register paths for files_generated event
        if (outKind == JavaFileObject.Kind.CLASS) {
          outputConsumer.registerCompiledClass(
            outputFile = fileObject.file,
            relativeOutputPath = fileObject.relativePath,
            compiled = CompiledClass(fileObject.file, sourceIoFiles, fileObject.className, content),
            builderName = "java",
            sourceFiles = sourceFiles,
          )
        }
        else {
          outputConsumer.registerOutputFile(
            target = rootDescriptor.target,
            outputFile = fileObject.file,
            sourcePaths = sourcePaths,
          )
        }
      }

      if (outKind == JavaFileObject.Kind.CLASS && mappingsCallback != null) {
        // register in mappings any non-temp class file
        val fileName = if (JavaBuilderUtil.isDepGraphEnabled()) {
          FileUtilRt.toSystemIndependentName(fileObject.relativePath)
        }
        else {
          fileObject.file.invariantSeparatorsPath
        }
        val reader = FailSafeClassReader(content.buffer, content.offset, content.length)
        mappingsCallback.associate(fileName, sourcePaths, reader, fileObject.isGenerated)
      }
    }

    if (outKind == JavaFileObject.Kind.CLASS && !sourceFiles.isEmpty()) {
      successfullyCompiled.addAll(sourceIoFiles)
    }
  }
}

private class DiagnosticSink(
  private val context: CompileContext,
  private val registrars: MutableCollection<JavacFileReferencesRegistrar>,
  private val out: Appendable,
) : DiagnosticOutputConsumer {
  private val myErrorCount = AtomicInteger(0)
  private val myWarningCount = AtomicInteger(0)
  @JvmField
  val filesWithErrors = hashSet<File>()

  override fun javaFileLoaded(file: File?) {
  }

  override fun registerJavacFileData(data: JavacFileData) {
    for (registrar in registrars) {
      registrar.registerFile(
        context,
        data.filePath,
        data.refs.entries,
        data.defs,
        data.casts,
        data.implicitToStringRefs
      )
    }
  }

  override fun customOutputData(pluginId: String, dataName: String?, data: ByteArray) {
    if (JavacFileData.CUSTOM_DATA_PLUGIN_ID == pluginId && JavacFileData.CUSTOM_DATA_KIND == dataName) {
      registerJavacFileData(JavacFileData.fromBytes(data))
    }
    else {
      for (listener in JpsServiceManager.getInstance().getExtensions(CustomOutputDataListener::class.java)) {
        if (pluginId == listener.id) {
          listener.processData(context, dataName, data)
          return
        }
      }
    }
  }

  override fun outputLineAvailable(line: @NlsSafe String?) {
    if (line.isNullOrEmpty()) {
      return
    }

    when {
      line.startsWith(ExternalJavacManager.STDOUT_LINE_PREFIX) || line.startsWith(ExternalJavacManager.STDERR_LINE_PREFIX) -> {
        out.appendLine(line)
      }

      line.contains("java.lang.OutOfMemoryError") -> {
        context.processMessage(CompilerMessage("java", BuildMessage.Kind.ERROR, JpsBuildBundle.message("build.message.insufficient.memory")))
        myErrorCount.incrementAndGet()
      }

      else -> {
        out.appendLine(line)
      }
    }
  }

  override fun report(diagnostic: Diagnostic<out JavaFileObject>) {
    val kind = when (diagnostic.kind) {
      Diagnostic.Kind.ERROR -> {
        myErrorCount.incrementAndGet()
        BuildMessage.Kind.ERROR
      }

      Diagnostic.Kind.MANDATORY_WARNING, Diagnostic.Kind.WARNING -> {
        myWarningCount.incrementAndGet()
        BuildMessage.Kind.WARNING
      }

      Diagnostic.Kind.NOTE -> BuildMessage.Kind.INFO
      Diagnostic.Kind.OTHER -> if (diagnostic is JpsInfoDiagnostic) BuildMessage.Kind.JPS_INFO else BuildMessage.Kind.OTHER
      else -> BuildMessage.Kind.OTHER
    }

    val source: JavaFileObject? = diagnostic.getSource()
    val sourceFile = if (source == null) null else File(source.toUri())
    val sourcePath = if (sourceFile == null) {
      null
    }
    else {
      if (kind == BuildMessage.Kind.ERROR) {
        filesWithErrors.add(sourceFile)
      }
      sourceFile.invariantSeparatorsPath
    }
    val message = diagnostic.getMessage(Locale.US)
    context.processMessage(CompilerMessage(
      "java",
      kind,
      message,
      sourcePath,
      diagnostic.startPosition,
      diagnostic.endPosition,
      diagnostic.position,
      diagnostic.lineNumber,
      diagnostic.columnNumber,
    ))
  }

  val errorCount: Int
    get() = myErrorCount.get()

  val warningCount: Int
    get() = myWarningCount.get()
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
  val result = hashSet<String>()
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

private fun buildOutputDirectoriesMap(chunk: ModuleChunk): Map<File, Set<File>> {
  val target = chunk.targets.single() as BazelModuleBuildTarget
  val roots = HashSet<File>(target.sources.size)
  target.sources.mapTo(roots) { it.toFile() }
  @Suppress("RemoveRedundantQualifierName")
  return java.util.Map.of(target.outputDir, roots)
}

internal fun getAssociatedSdk(chunk: ModuleChunk): Pair<JpsSdk<JpsDummyElement?>, Int>? {
  // assuming all modules in the chunk have the same associated JDK,
  // this constraint should be validated on build start
  val sdk = chunk.representativeTarget().module.getSdk(JpsJavaSdkType.INSTANCE) ?: return null
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
  chunk: ModuleChunk,
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
      chunk = chunk,
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
  chunk: ModuleChunk,
  profile: ProcessorConfigProfile?,
  compilingTool: JavaCompilingTool,
): Pair<Iterable<String>, Iterable<String>> {
  val compilationOptions = ArrayList<String>()
  val vmOptions = ArrayList<String>()
  if (!JavacMain.TRACK_AP_GENERATED_DEPENDENCIES) {
    vmOptions.add("-D" + JavacMain.TRACK_AP_GENERATED_DEPENDENCIES_PROPERTY + "=false")
    notifyMessage(context, BuildMessage.Kind.WARNING, "build.message.incremental.annotation.processing.disabled.0", true, JavacMain.TRACK_AP_GENERATED_DEPENDENCIES_PROPERTY)
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

  var customArgs = compilerOptions.ADDITIONAL_OPTIONS_STRING
  val overrideMap = compilerOptions.ADDITIONAL_OPTIONS_OVERRIDE
  if (!overrideMap.isEmpty()) {
    for (m in chunk.modules) {
      val overridden = overrideMap.get(m.name)
      if (overridden != null) {
        customArgs = overridden
        break
      }
    }
  }

  if (customArgs != null && !customArgs.isEmpty()) {
    var appender = BiConsumer { obj: MutableList<String>, e: String -> obj.add(e) }
    val module = chunk.representativeTarget().module
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
        notifyOptionIgnored(context, userOption, chunk)
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
          if (POSSIBLY_CONFLICTING_OPTIONS.contains(userOption)) {
            notifyOptionPossibleConflicts(context, userOption, chunk)
          }
          if (userOption.startsWith("-J-")) {
            vmOptions.add(userOption.substring("-J".length))
          }
          else {
            appender.accept(compilationOptions, userOption)
          }
        }
        else {
          notifyOptionIgnored(context, userOption, chunk)
        }
      }
    }
  }

  for (extension in JpsServiceManager.getInstance().getExtensions(ExternalJavacOptionsProvider::class.java)) {
    vmOptions.addAll(extension.getOptions(compilingTool, compilerSdkVersion))
  }

  addCompilationOptions(
    compilerSdkVersion = compilerSdkVersion,
    options = compilationOptions,
    chunk = chunk,
    profile = profile,
  )

  return Pair(vmOptions, compilationOptions)
}

private fun notifyOptionPossibleConflicts(context: CompileContext, option: String, chunk: ModuleChunk) {
  notifyMessage(context, BuildMessage.Kind.JPS_INFO, "build.message.user.specified.option.0.for.1.may.conflict.with.calculated.option", false, option, chunk.presentableShortName)
}

private fun notifyOptionIgnored(context: CompileContext, option: String, chunk: ModuleChunk) {
  notifyMessage(context, BuildMessage.Kind.JPS_INFO, "build.message.user.specified.option.0.is.ignored.for.1", false, option, chunk.presentableShortName)
}

private fun notifyMessage(context: CompileContext, kind: BuildMessage.Kind?, messageKey: String, notifyOnce: Boolean, vararg params: Any?) {
  if (!notifyOnce || SHOWN_NOTIFICATIONS.get(context)!!.add(messageKey)) {
    context.processMessage(CompilerMessage("java", kind, JpsBuildBundle.message(messageKey, *params)))
  }
}

private fun addCompilationOptions(
  compilerSdkVersion: Int,
  options: MutableList<String>,
  chunk: ModuleChunk,
  profile: ProcessorConfigProfile?,
) {
  addCrossCompilationOptions(compilerSdkVersion, options, chunk)

  if (!options.contains(ENABLE_PREVIEW_OPTION)) {
    val level = JpsJavaExtensionService.getInstance().getLanguageLevel(chunk.representativeTarget().module)
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

    val sourceOutput = ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(
      chunk.modules.iterator().next(), chunk.containsTests(), profile
    )
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

private fun addCrossCompilationOptions(compilerSdkVersion: Int, options: MutableList<String>, chunk: ModuleChunk) {
  val module = chunk.representativeTarget().module
  val level = requireNotNull(JpsJavaExtensionService.getInstance().getLanguageLevel(module)) {
    "Language level must be set for module ${module.name}"
  }
  if (level.isPreview) {
    options.add(ENABLE_PREVIEW_OPTION)
  }

  require(level != LanguageLevel.JDK_X)

  val bytecodeTarget = level.feature()
  require(bytecodeTarget > 0)

  if (shouldUseReleaseOption(compilerSdkVersion, bytecodeTarget, bytecodeTarget)) {
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
    val associatedSdk = getAssociatedSdk(chunk)
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

/**
 * The assumed module's source code language version.
 * Returns the version number, corresponding to the language level, associated with the given module.
 * If no language level set (neither on module- nor on project-level), the version of JDK associated with the module is returned.
 * If no JDK is associated, returns 0.
 */
private fun getTargetPlatformLanguageVersion(module: JpsModule): Int {
  val level = JpsJavaExtensionService.getInstance().getLanguageLevel(module)?.feature() ?: 0
  if (level > 0) {
    return level
  }

  // when compiling, if language level is not explicitly set, it is assumed to be equal to
  // the highest possible language level supported by target JDK
  module.getSdk(JpsJavaSdkType.INSTANCE)?.let {
    return JpsJavaSdkType.getJavaVersion(it)
  }
  return 0
}