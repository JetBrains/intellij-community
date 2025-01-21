@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "UnstableApiUsage", "HardCodedStringLiteral", "ReplaceGetOrSet", "SSBasedInspection", "LoggingSimilarMessage")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.compiler.instrumentation.FailSafeClassReader
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.io.BaseOutputReader
import com.intellij.util.lang.JavaVersion
import org.jetbrains.bazel.jvm.jps.hashMap
import org.jetbrains.bazel.jvm.jps.hashSet
import org.jetbrains.bazel.jvm.jps.impl.JavaBuilder.Companion.builderName
import org.jetbrains.bazel.jvm.jps.linkedSet
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.ProjectPaths
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
import org.jetbrains.jps.builders.*
import org.jetbrains.jps.builders.impl.DirtyFilesHolderBase
import org.jetbrains.jps.builders.impl.TargetOutputIndexImpl
import org.jetbrains.jps.builders.java.*
import org.jetbrains.jps.builders.java.dependencyView.Callbacks.Backend
import org.jetbrains.jps.cmdline.ClasspathBootstrap
import org.jetbrains.jps.incremental.*
import org.jetbrains.jps.incremental.ModuleLevelBuilder.OutputConsumer
import org.jetbrains.jps.incremental.java.*
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.incremental.messages.CompilerMessage
import org.jetbrains.jps.javac.*
import org.jetbrains.jps.javac.ast.api.JavacFileData
import org.jetbrains.jps.model.JpsDummyElement
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.java.compiler.*
import org.jetbrains.jps.model.library.sdk.JpsSdk
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.jetbrains.jps.service.JpsServiceManager
import org.jetbrains.jps.service.SharedThreadPool
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.lang.Boolean
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import javax.tools.Diagnostic
import javax.tools.DiagnosticListener
import javax.tools.JavaFileObject
import kotlin.Any
import kotlin.ByteArray
import kotlin.Exception
import kotlin.Int
import kotlin.Long
import kotlin.RuntimeException
import kotlin.String
import kotlin.Suppress
import kotlin.Throwable
import kotlin.Throws
import kotlin.also
import kotlin.checkNotNull
import kotlin.let
import kotlin.synchronized

private val LOG = Logger.getInstance(JavaBuilder::class.java)
private const val JAVA_EXTENSION = "java"
private const val USE_MODULE_PATH_ONLY_OPTION = "compiler.force.module.path"

@Suppress("RemoveRedundantQualifierName")
internal val javaModuleTypes = java.util.Set.of(JpsJavaModuleType.INSTANCE)

internal class JavaBuilder() : ModuleLevelBuilder(BuilderCategory.TRANSLATOR) {
  private val refRegistrars = ArrayList<JavacFileReferencesRegistrar>()

  companion object {
    const val BUILDER_ID: String = "java"

    private val PREFER_TARGET_JDK_COMPILER: Key<kotlin.Boolean?> = GlobalContextKey.create<kotlin.Boolean?>("_prefer_target_jdk_javac_")
    private val SHOWN_NOTIFICATIONS: Key<MutableSet<String>?> = GlobalContextKey.create<MutableSet<String>?>("_shown_notifications_")
    private val COMPILING_TOOL = Key.create<JavaCompilingTool?>("_java_compiling_tool_")
    private val COMPILER_USAGE_STATISTICS = Key.create<ConcurrentMap<String?, MutableCollection<String>>?>("_java_compiler_usage_stats_")
    private val MODULE_PATH_SPLITTER: Key<ModulePathSplitter> = GlobalContextKey.create<ModulePathSplitter?>("_module_path_splitter_")
    private val COMPILABLE_EXTENSIONS = mutableListOf<String>(JAVA_EXTENSION)

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
    private val FILTERED_OPTIONS = java.util.Set.of<String?>(TARGET_OPTION, RELEASE_OPTION, "-d")
    @Suppress("RemoveRedundantQualifierName", "SpellCheckingInspection")
    private val FILTERED_SINGLE_OPTIONS: MutableSet<String?> = java.util.Set.of(
      "-g", "-deprecation", "-nowarn", "-verbose", PROC_NONE_OPTION, PROC_ONLY_OPTION, PROC_FULL_OPTION, "-proceedOnError"
    )
    @Suppress("RemoveRedundantQualifierName", "SpellCheckingInspection")
    private val POSSIBLY_CONFLICTING_OPTIONS = java.util.Set.of(
      SOURCE_OPTION, SYSTEM_OPTION, "--boot-class-path", "-bootclasspath", "--class-path", "-classpath", "-cp", PROCESSORPATH_OPTION, "-sourcepath", "--module-path", "-p", "--module-source-path"
    )

    private val ourDefaultRtJar: File?
    private val ourProcFullRequiredFrom: Int // 0, if not set

    init {
      var rtJar: File? = null
      val tokenizer = StringTokenizer(System.getProperty("sun.boot.class.path", ""), File.pathSeparator, false)
      while (tokenizer.hasMoreTokens()) {
        val file = File(tokenizer.nextToken())
        if ("rt.jar" == file.getName()) {
          rtJar = file
          break
        }
      }
      ourDefaultRtJar = rtJar
      ourProcFullRequiredFrom = 0
    }

    val builderName: String
      get() = "java"

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

    private fun logJavacCall(chunk: ModuleChunk, options: Iterable<String?>, mode: String?) {
      if (LOG.isDebugEnabled) {
        LOG.debug((if (Iterators.contains<String?>(options, PROC_ONLY_OPTION)) "Running processors for chunk" else "Compiling chunk") +
          " [" + chunk.name + "] with options: \"" + options.joinToString(" ") + "\", mode=" + mode)
      }
    }

    private fun invokeJavac(
      compilerSdkVersion: Int,
      context: CompileContext,
      chunk: ModuleChunk,
      compilingTool: JavaCompilingTool,
      options: Iterable<String>,
      files: Iterable<File>?,
      outSink: OutputFileConsumer,
      javacCall: JavacCaller
    ): kotlin.Boolean {
      if (Iterators.contains<String?>(options, PROC_ONLY_OPTION)) {
        // make a dedicated javac call for annotation processing only
        val generated: MutableCollection<File> = ArrayList<File>()
        val processingSuccess = javacCall.invoke(options, files, OutputFileConsumer { fileObject: OutputFileObject? ->
          if (fileObject!!.getKind() == JavaFileObject.Kind.SOURCE) {
            generated.add(fileObject.file)
          }
          outSink.save(fileObject)
        })
        if (!processingSuccess) {
          return false
        }
        // now call javac with processor-generated sources and without processing-related options
        val compileOnlyOptions = getCompilationOptions(compilerSdkVersion, context, chunk, null, compilingTool).second
        return javacCall.invoke(compileOnlyOptions, Iterators.flat<File?>(files, generated), outSink)
      }

      return javacCall.invoke(options, files, outSink)
    }

    private fun updateCompilerUsageStatistics(context: CompileContext?, compilerName: String?, chunk: ModuleChunk) {
      val map = COMPILER_USAGE_STATISTICS.get(context)
      var names = map.get(compilerName)
      if (names == null) {
        names = Collections.synchronizedSet(HashSet())
        val prev = map.putIfAbsent(compilerName, names)
        if (prev != null) {
          names = prev
        }
      }
      for (module in chunk.modules) {
        names.add(module.name)
      }
    }

    private fun shouldUseReleaseOption(config: JpsJavaCompilerConfiguration, compilerVersion: Int, chunkSdkVersion: Int, targetPlatformVersion: Int): kotlin.Boolean {
      if (!config.useReleaseOption()) {
        return false
      }

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

    private fun shouldForkCompilerProcess(context: CompileContext, chunk: ModuleChunk, chunkLanguageLevel: Int): kotlin.Boolean {
      val compilerSdkVersion = JavaVersion.current().feature

      if (preferTargetJdkCompiler(context)) {
        val sdkVersionPair = getAssociatedSdk(chunk)
        if (sdkVersionPair != null) {
          val chunkSdkVersion = sdkVersionPair.second
          if (chunkSdkVersion != compilerSdkVersion && chunkSdkVersion >= ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION) {
            // there is a special case because of difference in type inference behavior between javac8 and javac6-javac7
            // so if the corresponding JDK is associated with the module chunk, prefer compiler from this JDK over the newer compiler version
            return true
          }
        }
      }

      if (chunkLanguageLevel <= 0) {
        // was not able to determine jdk version, so assuming an in-process compiler
        return false
      }
      return !isTargetReleaseSupported(compilerSdkVersion, chunkLanguageLevel)
    }

    private fun isTargetReleaseSupported(compilerVersion: Int, targetPlatformVersion: Int): kotlin.Boolean {
      if (targetPlatformVersion > compilerVersion) {
        return false
      }
      if (compilerVersion < 9) {
        return true
      }
      if (compilerVersion <= 11) {
        return targetPlatformVersion >= 6
      }
      if (compilerVersion <= 19) {
        return targetPlatformVersion >= 7
      }
      return targetPlatformVersion >= 8
    }

    private fun isJavac(compilingTool: JavaCompilingTool?): kotlin.Boolean {
      return compilingTool != null && (compilingTool.id == JavaCompilers.JAVAC_ID || compilingTool.id == JavaCompilers.JAVAC_API_ID)
    }

    private fun preferTargetJdkCompiler(context: CompileContext): kotlin.Boolean {
      var `val` = PREFER_TARGET_JDK_COMPILER.get(context)
      if (`val` == null) {
        val config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(context.projectDescriptor.project)
        // default
        PREFER_TARGET_JDK_COMPILER.set(context, config.getCompilerOptions(JavaCompilers.JAVAC_ID).PREFER_TARGET_JDK_COMPILER.also { `val` = it })
      }
      return `val`!!
    }

    // If platformCp of the build process is the same as the target platform, do not specify platformCp explicitly
    // this will allow javac to resolve against ct.sym file, which is required for the "compilation profiles" feature
    private fun calcEffectivePlatformCp(platformCp: Collection<File>, options: Iterable<String>, compilingTool: JavaCompilingTool?): Iterable<File>? {
      if (ourDefaultRtJar == null || !isJavac(compilingTool)) {
        return platformCp
      }
      var profileFeatureRequested = false
      for (option in options) {
        if ("-profile".equals(option, ignoreCase = true)) {
          profileFeatureRequested = true
          break
        }
      }
      if (!profileFeatureRequested) {
        return platformCp
      }
      var isTargetPlatformSameAsBuildRuntime = false
      for (file in platformCp) {
        if (FileUtil.filesEqual(file, ourDefaultRtJar)) {
          isTargetPlatformSameAsBuildRuntime = true
          break
        }
      }
      if (!isTargetPlatformSameAsBuildRuntime) {
        // compact profile was requested, but we have to use alternative platform classpath to meet project settings
        // consider this a compiler error and let user re-configure the project
        return null
      }
      // returning an empty list will force default behavior for platform classpath calculation
      // javac will resolve against its own bootclasspath and use ct.sym file when available
      return emptyList()
    }

    @Synchronized
    @Throws(IOException::class)
    private fun ensureJavacServerStarted(context: CompileContext): ExternalJavacManager {
      var server = ExternalJavacManagerKey.KEY.get(context)
      if (server != null) {
        return server
      }

      val listenPort = findFreePort()
      server = object : ExternalJavacManager(Utils.getSystemRoot(), SharedThreadPool.getInstance(), 2 * 60 * 1000L /*keep idle builds for 2 minutes*/) {
        override fun createProcessHandler(processId: UUID?, process: Process, commandLine: String, keepProcessAlive: kotlin.Boolean): ExternalJavacProcessHandler {
          return object : ExternalJavacProcessHandler(processId, process, commandLine, keepProcessAlive) {
            override fun executeTask(task: Runnable): Future<*> {
              return SharedThreadPool.getInstance().submit(task)
            }

            override fun readerOptions(): BaseOutputReader.Options {
              return BaseOutputReader.Options.NON_BLOCKING
            }
          }
        }
      }
      server.start(listenPort)
      ExternalJavacManagerKey.KEY.set(context, server)
      return server
    }

    private fun findFreePort(): Int {
      try {
        val serverSocket = ServerSocket(0)
        try {
          return serverSocket.getLocalPort()
        }
        finally {
          //workaround for linux : calling close() immediately after opening socket
          //may result that socket is not closed
          synchronized(serverSocket) {
            try {
              (serverSocket as Object).wait(1)
            }
            catch (_: Throwable) {
            }
          }
          serverSocket.close()
        }
      }
      catch (e: IOException) {
        e.printStackTrace(System.err)
        return ExternalJavacManager.DEFAULT_SERVER_PORT
      }
    }

    private val USER_DEFINED_BYTECODE_TARGET = Key.create<String?>("_user_defined_bytecode_target_")

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
        ClasspathBootstrap.configureReflectionOpenPackages(Consumer { p -> vmOptions.add(p) })
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
          val moduleDirPath = FileUtil.toCanonicalPath(baseDirectory.absolutePath)
          appender = BiConsumer { strings, option -> strings.add(option.replace(PathMacroUtil.DEPRECATED_MODULE_DIR, moduleDirPath!!)) }
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

      for (extension in JpsServiceManager.getInstance().getExtensions<ExternalJavacOptionsProvider?>(ExternalJavacOptionsProvider::class.java)) {
        vmOptions.addAll(extension.getOptions(compilingTool, compilerSdkVersion))
      }

      addCompilationOptions(
        compilerSdkVersion = compilerSdkVersion,
        compilingTool = compilingTool,
        options = compilationOptions,
        context = context,
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

    private fun notifyMessage(context: CompileContext, kind: BuildMessage.Kind?, messageKey: String, notifyOnce: kotlin.Boolean, vararg params: Any?) {
      if (!notifyOnce || SHOWN_NOTIFICATIONS.get(context)!!.add(messageKey)) {
        context.processMessage(CompilerMessage(builderName, kind, JpsBuildBundle.message(messageKey, *params)))
      }
    }

    private fun addCompilationOptions(
      compilerSdkVersion: Int,
      compilingTool: JavaCompilingTool?,
      options: MutableList<String>,
      context: CompileContext,
      chunk: ModuleChunk,
      profile: ProcessorConfigProfile?,
    ) {
      addCrossCompilationOptions(compilerSdkVersion, options, context, chunk)

      if (!options.contains(ENABLE_PREVIEW_OPTION)) {
        val level = JpsJavaExtensionService.getInstance().getLanguageLevel(chunk.representativeTarget().module)
        if (level != null && level.isPreview) {
          options.add(ENABLE_PREVIEW_OPTION)
        }
      }

      if (addAnnotationProcessingOptions(options, profile)) {
        checkNotNull(profile)

        if (profile.isProcOnly) {
          options.add(PROC_ONLY_OPTION)
        }
        else {
          // for newer compilers need to enable annotation processing explicitly

          if (ourProcFullRequiredFrom > 0 /*the requirement explicitly configured*/) {
            if (compilerSdkVersion >= ourProcFullRequiredFrom) {
              options.add(PROC_FULL_OPTION)
            }
          }
          else {
            // by default required for javac from version 23
            if (compilerSdkVersion > 22 && isJavac(compilingTool)) {
              options.add(PROC_FULL_OPTION)
            }
          }
        }

        val srcOutput = ProjectPaths.getAnnotationProcessorGeneratedSourcesOutputDir(
          chunk.modules.iterator().next(), chunk.containsTests(), profile
        )
        if (srcOutput != null) {
          FileUtil.createDirectory(srcOutput)
          options.add("-s")
          options.add(srcOutput.path)
        }
      }
    }

    /**
     * @return true if annotation processing is enabled and corresponding options were added, false if the profile is null or disabled
     */
    fun addAnnotationProcessingOptions(options: MutableList<String>, profile: AnnotationProcessingConfiguration?): kotlin.Boolean {
      if (profile == null || !profile.isEnabled) {
        options.add(PROC_NONE_OPTION)
        return false
      }

      // configuring annotation processing
      if (!profile.isObtainProcessorsFromClasspath) {
        val processorsPath = profile.processorPath
        options.add(if (profile.isUseProcessorModulePath) PROCESSOR_MODULE_PATH_OPTION else PROCESSORPATH_OPTION)
        options.add(FileUtil.toSystemDependentName(processorsPath.trim { it <= ' ' }))
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

    private fun addCrossCompilationOptions(compilerSdkVersion: Int, options: MutableList<String>, context: CompileContext, chunk: ModuleChunk) {
      val compilerConfiguration = JpsJavaExtensionService.getInstance().getCompilerConfiguration(
        context.projectDescriptor.project
      )

      val module = chunk.representativeTarget().module
      val level = JpsJavaExtensionService.getInstance().getLanguageLevel(module)
      val chunkSdkVersion = getChunkSdkVersion(chunk)
      val languageLevel = if (level == null) 0 else if (level == LanguageLevel.JDK_X) chunkSdkVersion else level.feature()
      var bytecodeTarget = if (level == LanguageLevel.JDK_X) chunkSdkVersion else getModuleBytecodeTarget(context, chunk, compilerConfiguration, languageLevel)

      if (shouldUseReleaseOption(compilerConfiguration, compilerSdkVersion, chunkSdkVersion, bytecodeTarget)) {
        options.add(RELEASE_OPTION)
        options.add(complianceOption(bytecodeTarget))
        return
      }

      // alternatively, using `-source`, `-target` and `--system` (or `-bootclasspath`) options
      if (languageLevel > 0 && !options.contains(SOURCE_OPTION)) {
        options.add(SOURCE_OPTION)
        options.add(complianceOption(languageLevel))
      }

      if (bytecodeTarget > 0) {
        if (chunkSdkVersion > 0 && compilerSdkVersion > chunkSdkVersion) {
          // if compiler is newer than module JDK
          if (compilerSdkVersion >= bytecodeTarget) {
            // if a user-specified bytecode version can be determined and is supported by compiler
            if (bytecodeTarget > chunkSdkVersion) {
              // and the user-specified bytecode target level is higher than the highest one supported by the target JDK,
              // force compiler to use a highest-available bytecode target version that is supported by the chunk JDK.
              bytecodeTarget = chunkSdkVersion
            }
          }
          // otherwise, let compiler display compilation error about an incorrectly set bytecode target version
        }
      }
      else {
        if (chunkSdkVersion > 0 && compilerSdkVersion > chunkSdkVersion) {
          // force lower bytecode target level to match the version of the chunk JDK
          bytecodeTarget = chunkSdkVersion
        }
      }

      if (bytecodeTarget > 0) {
        options.add(TARGET_OPTION)
        options.add(complianceOption(bytecodeTarget))
      }

      if (compilerSdkVersion >= 9) {
        val associatedSdk = getAssociatedSdk(chunk)
        if (associatedSdk != null && associatedSdk.getSecond() >= 9 && associatedSdk.getSecond() != compilerSdkVersion) {
          val homePath = associatedSdk.getFirst().homePath
          if (homePath != null) {
            options.add(SYSTEM_OPTION)
            options.add(FileUtil.toSystemIndependentName(homePath))
          }
        }
      }
    }

    private fun getModuleBytecodeTarget(context: CompileContext?, chunk: ModuleChunk, compilerConfiguration: JpsJavaCompilerConfiguration, languageLevel: Int): Int {
      var bytecodeTarget = 0
      for (module in chunk.modules) {
        // use the lower possible target among modules that form the chunk
        val moduleTarget = JpsJavaSdkType.parseVersion(compilerConfiguration.getByteCodeTargetLevel(module.name))
        if (moduleTarget > 0 && (bytecodeTarget == 0 || moduleTarget < bytecodeTarget)) {
          bytecodeTarget = moduleTarget
        }
      }
      if (bytecodeTarget == 0) {
        if (languageLevel > 0) {
          // according to IDEA rule: if not specified explicitly, set target to be the same as source language level
          bytecodeTarget = languageLevel
        }
        else {
          // last resort and backward compatibility:
          // check if user explicitly defined bytecode target in additional compiler options
          val value = USER_DEFINED_BYTECODE_TARGET.get(context)
          if (value != null) {
            bytecodeTarget = JpsJavaSdkType.parseVersion(value)
          }
        }
      }
      return bytecodeTarget
    }

    private fun complianceOption(major: Int): String {
      return JpsJavaSdkType.complianceOption(JavaVersion.compose(major))
    }

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

    private fun getChunkSdkVersion(chunk: ModuleChunk): Int {
      var chunkSdkVersion = -1
      for (module in chunk.modules) {
        val sdk = module.getSdk<JpsDummyElement?>(JpsJavaSdkType.INSTANCE)
        if (sdk != null) {
          val moduleSdkVersion = JpsJavaSdkType.getJavaVersion(sdk)
          if (moduleSdkVersion != 0 /*could determine the version*/ && (chunkSdkVersion < 0 || chunkSdkVersion > moduleSdkVersion)) {
            chunkSdkVersion = moduleSdkVersion
          }
        }
      }
      return chunkSdkVersion
    }

    private fun getForkedJavacSdk(diagnostic: DiagnosticListener<in JavaFileObject?>, chunk: ModuleChunk, targetLanguageLevel: Int): Pair<String?, Int?>? {
      val associatedSdk = getAssociatedSdk(chunk)
      var canRunAssociatedJavac = false
      if (associatedSdk != null) {
        val sdkVersion = associatedSdk.second
        canRunAssociatedJavac = sdkVersion >= ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION
        if (isTargetReleaseSupported(sdkVersion, targetLanguageLevel)) {
          if (canRunAssociatedJavac) {
            return Pair.create<String?, Int?>(associatedSdk.first.homePath, sdkVersion)
          }
        }
        else {
          LOG.warn("Target bytecode version " + targetLanguageLevel + " is not supported by SDK " + sdkVersion + " associated with module " + chunk.name)
        }
      }

      val fallbackJdkHome = System.getProperty(GlobalOptions.FALLBACK_JDK_HOME, null)
      if (fallbackJdkHome == null) {
        LOG.info("Fallback JDK is not specified. (See " + GlobalOptions.FALLBACK_JDK_HOME + " option)")
      }
      val fallbackJdkVersion = System.getProperty(GlobalOptions.FALLBACK_JDK_VERSION, null)
      if (fallbackJdkVersion == null) {
        LOG.info("Fallback JDK version is not specified. (See " + GlobalOptions.FALLBACK_JDK_VERSION + " option)")
      }

      if (associatedSdk == null && (fallbackJdkHome == null || fallbackJdkVersion == null)) {
        diagnostic.report(PlainMessageDiagnostic(Diagnostic.Kind.ERROR, JpsBuildBundle.message("build.message.cannot.start.javac.process.for.0.unknown.jdk.home", chunk.name)))
        return null
      }

      // either associatedSdk or fallbackJdk is configured, but associatedSdk cannot be used
      if (fallbackJdkHome != null) {
        val fallbackVersion = JpsJavaSdkType.parseVersion(fallbackJdkVersion)
        if (isTargetReleaseSupported(fallbackVersion, targetLanguageLevel)) {
          if (fallbackVersion >= ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION) {
            return Pair.create<String?, Int?>(fallbackJdkHome, fallbackVersion)
          }
          else {
            LOG.info("Version string for fallback JDK is '" + fallbackJdkVersion + "' (recognized as version '" + fallbackVersion + "')." +
              " At least version " + ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION + " is required to launch javac process.")
          }
        }
      }

      // at this point, fallbackJdk is not suitable either
      if (associatedSdk != null) {
        if (canRunAssociatedJavac) {
          // although target release is not supported, attempt to start javac, so that javac properly reports this error
          return Pair.create<String?, Int?>(associatedSdk.first.homePath, associatedSdk.second)
        }
        else {
          diagnostic.report(PlainMessageDiagnostic(Diagnostic.Kind.ERROR,
            JpsBuildBundle.message(
              "build.message.unsupported.javac.version",
              chunk.name,
              associatedSdk.second,
              ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION,
              targetLanguageLevel
            )
          ))
        }
      }

      return null
    }

    private fun getAssociatedSdk(chunk: ModuleChunk): Pair<JpsSdk<JpsDummyElement?>, Int>? {
      // assuming all modules in the chunk have the same associated JDK,
      // this constraint should be validated on build start
      val sdk = chunk.representativeTarget().module.getSdk(JpsJavaSdkType.INSTANCE) ?: return null
      return Pair(sdk, JpsJavaSdkType.getJavaVersion(sdk))
    }

    private fun buildOutputDirectoriesMap(context: CompileContext, chunk: ModuleChunk): Map<File, Set<File>> {
      val map = hashMap<File, MutableSet<File>>()
      for (target in chunk.targets) {
        val outputDir = target.outputDir ?: continue
        val roots = hashSet<File>()
        for (descriptor in context.projectDescriptor.buildRootIndex.getTargetRoots(target, context)) {
          roots.add(descriptor.root)
        }
        map.put(outputDir, roots)
      }
      return map
    }
  }

  override fun getPresentableName(): String = builderName

  override fun buildStarted(context: CompileContext) {
    val project = context.projectDescriptor.project
    val compilerId = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).javaCompilerId
    MODULE_PATH_SPLITTER.set(context, ModulePathSplitter(ExplodedModuleNameFinder(context)))
    val compilingTool = JavaBuilderUtil.findCompilingTool(compilerId)
    COMPILING_TOOL.set(context, compilingTool)
    SHOWN_NOTIFICATIONS.set(context, Collections.synchronizedSet(HashSet<String>()))
    COMPILER_USAGE_STATISTICS.set(context, ConcurrentHashMap<String, MutableCollection<String>>())
    val dataManager = context.projectDescriptor.dataManager
    if (!isJavac(compilingTool)) {
      dataManager.isProcessConstantsIncrementally = false
    }
    JavaBackwardReferenceIndexWriter.initialize(context)
    for (registrar in JpsServiceManager.getInstance().getExtensions(JavacFileReferencesRegistrar::class.java)) {
      if (registrar.isEnabled) {
        registrar.initialize()
        refRegistrars.add(registrar)
      }
    }
  }

  override fun chunkBuildStarted(context: CompileContext?, chunk: ModuleChunk) {
    // before the first compilation round starts: find and mark dirty all classes that depend on removed or moved classes so
    // that all such files are compiled in the first round.
    JavaBuilderUtil.markDirtyDependenciesForInitialRound(context, object : DirtyFilesHolderBase<JavaSourceRootDescriptor, ModuleBuildTarget>(context) {
      override fun processDirtyFiles(processor: FileProcessor<JavaSourceRootDescriptor, ModuleBuildTarget>) {
        FSOperations.processFilesToRecompile(context, chunk, processor)
      }
    }, chunk)
  }

  override fun buildFinished(context: CompileContext) {
    refRegistrars.clear()
    val stats = COMPILER_USAGE_STATISTICS.get(context)
    if (stats.size == 1) {
      val entry = stats.entries.iterator().next()
      val compilerName = entry.key
      context.processMessage(CompilerMessage("", BuildMessage.Kind.JPS_INFO,
        JpsBuildBundle.message("build.message.0.was.used.to.compile.java.sources", compilerName)))
      LOG.info("$compilerName was used to compile ${entry.value}")
    }
    else {
      for (entry in stats.entries) {
        val compilerName = entry.key
        val moduleNames = entry.value
        context.processMessage(CompilerMessage("", BuildMessage.Kind.JPS_INFO,
          if (moduleNames.size == 1) JpsBuildBundle.message("build.message.0.was.used.to.compile.1", compilerName, moduleNames.iterator().next()) else JpsBuildBundle.message("build.message.0.was.used.to.compile.1.modules", compilerName, moduleNames.size)
        ))
        LOG.info("$compilerName was used to compile $moduleNames")
      }
    }
  }

  override fun getCompilableFileExtensions(): List<String> = COMPILABLE_EXTENSIONS

  override fun build(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
    outputConsumer: OutputConsumer,
  ): ExitCode? {
    return doBuild(
      context = context,
      chunk = chunk,
      dirtyFilesHolder = dirtyFilesHolder,
      outputConsumer = outputConsumer,
      compilingTool = COMPILING_TOOL.get(context),
    )
  }

  fun doBuild(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
    outputConsumer: OutputConsumer,
    compilingTool: JavaCompilingTool,
  ): ExitCode? {
    val filesToCompile = linkedSet<File>()
    dirtyFilesHolder.processDirtyFiles { target, file, descriptor ->
      if (file.path.endsWith(".java")) {
        filesToCompile.add(file)
      }
      true
    }

    var moduleInfoFile: File? = null
    var javaModulesCount = 0
    if ((!filesToCompile.isEmpty() || dirtyFilesHolder.hasRemovedFiles()) &&
      getTargetPlatformLanguageVersion(chunk.representativeTarget().module) >= 9) {
      for (target in chunk.targets) {
        val moduleInfo = JavaBuilderUtil.findModuleInfoFile(context, target)
        if (moduleInfo != null) {
          javaModulesCount++
          if (moduleInfoFile == null) {
            moduleInfoFile = moduleInfo
          }
        }
      }
    }

    if (JavaBuilderUtil.isCompileJavaIncrementally(context)) {
      val logger = context.loggingManager.projectBuilderLogger
      if (logger.isEnabled && !filesToCompile.isEmpty()) {
        logger.logCompiledFiles(filesToCompile, BUILDER_ID, "Compiling files:")
      }
    }

    if (javaModulesCount > 1) {
      val modules = chunk.modules.joinToString { it.name }
      context.processMessage(CompilerMessage(builderName, BuildMessage.Kind.ERROR,
        JpsBuildBundle.message("build.message.cannot.compile.a.module.cycle.with.multiple.module.info.files", modules)))
      return ExitCode.ABORT
    }

    return compile(
      context = context,
      chunk = chunk,
      dirtyFilesHolder = dirtyFilesHolder,
      files = filesToCompile,
      outputConsumer = outputConsumer,
      compilingTool = compilingTool,
      moduleInfoFile = moduleInfoFile
    )
  }

  private fun compile(
    context: CompileContext,
    chunk: ModuleChunk,
    dirtyFilesHolder: DirtyFilesHolder<JavaSourceRootDescriptor, ModuleBuildTarget>,
    files: Collection<File>,
    outputConsumer: OutputConsumer?,
    compilingTool: JavaCompilingTool,
    moduleInfoFile: File?,
  ): ExitCode {
    var exitCode = ExitCode.NOTHING_DONE

    val hasSourcesToCompile = !files.isEmpty()
    if (!hasSourcesToCompile && !dirtyFilesHolder.hasRemovedFiles()) {
      return exitCode
    }

    JavaBuilderUtil.ensureModuleHasJdk(chunk.representativeTarget().module, context, builderName)
    val classpath = ProjectPaths.getCompilationClasspath(chunk, false)
    val platformCp = ProjectPaths.getPlatformCompilationClasspath(chunk, false)

    // begin compilation round
    val outputSink = OutputFilesSink(
      context = context,
      outputConsumer = outputConsumer!!,
      mappingsCallback = JavaBuilderUtil.getDependenciesRegistrar(context),
      chunkName = chunk.presentableShortName,
    )
    var filesWithErrors: Collection<File>? = null
    try {
      if (hasSourcesToCompile) {
        exitCode = ExitCode.OK

        val diagnosticSink = DiagnosticSink(context, refRegistrars)
        val chunkName = chunk.name

        val filesCount = files.size
        var compiledOk = true
        if (filesCount > 0) {
          LOG.info("Compiling $filesCount java files; module: $chunkName${if (chunk.containsTests()) " (tests)" else ""}")
          if (LOG.isDebugEnabled) {
            for (file in files) {
              LOG.debug("Compiling " + file.path)
            }
            LOG.debug(" classpath for $chunkName:")
            for (file in classpath) {
              LOG.debug("  " + file.absolutePath)
            }
            LOG.debug(" platform classpath for $chunkName:")
            for (file in platformCp) {
              LOG.debug("  " + file.absolutePath)
            }
          }
          try {
            compiledOk = compileJava(
              context = context,
              chunk = chunk,
              files = files,
              originalClassPath = classpath,
              originalPlatformCp = platformCp,
              sourcePath = emptyList(),
              diagnosticSink = diagnosticSink,
              outputSink = outputSink,
              compilingTool = compilingTool,
              moduleInfoFile = moduleInfoFile,
            )
          }
          finally {
            filesWithErrors = diagnosticSink.filesWithErrors
          }
        }

        context.checkCanceled()

        if (!compiledOk && diagnosticSink.errorCount == 0) {
          // unexpected exception occurred or compiler did not output any errors for some reason
          diagnosticSink.report(PlainMessageDiagnostic(Diagnostic.Kind.ERROR, JpsBuildBundle.message("build.message.compilation.failed.internal.java.compiler.error")))
        }

        if (!Utils.PROCEED_ON_ERROR_KEY.get(context, Boolean.FALSE) && diagnosticSink.errorCount > 0) {
          throw StopBuildException(
            JpsBuildBundle.message("build.message.compilation.failed.errors.0.warnings.1", diagnosticSink.errorCount, diagnosticSink.warningCount)
          )
        }
      }
    }
    finally {
      JavaBuilderUtil.registerFilesToCompile(context, files)
      if (filesWithErrors != null) {
        JavaBuilderUtil.registerFilesWithErrors(context, filesWithErrors)
      }
      JavaBuilderUtil.registerSuccessfullyCompiled(context, outputSink.successfullyCompiled)
    }

    return exitCode
  }

  private fun compileJava(
    context: CompileContext,
    chunk: ModuleChunk,
    files: Collection<File>,
    originalClassPath: Collection<File>,
    originalPlatformCp: Collection<File>,
    sourcePath: Collection<File>,
    diagnosticSink: DiagnosticOutputConsumer,
    outputSink: OutputFileConsumer?,
    compilingTool: JavaCompilingTool,
    moduleInfoFile: File?,
  ): kotlin.Boolean {
    val modules = chunk.modules
    var profile: ProcessorConfigProfile? = null

    val compilerConfig = JpsJavaExtensionService.getInstance().getCompilerConfiguration(
      context.projectDescriptor.project
    )

    if (modules.size == 1) {
      profile = compilerConfig.getAnnotationProcessingProfile(modules.iterator().next())
    }
    val outs = buildOutputDirectoriesMap(context, chunk)
    val targetLanguageLevel = getTargetPlatformLanguageVersion(chunk.representativeTarget().module)
    // when, forking external javac, compilers from SDK 1.7 and higher are supported
    val forkSdk: Pair<String?, Int?>?
    if (shouldForkCompilerProcess(context, chunk, targetLanguageLevel)) {
      forkSdk = getForkedJavacSdk(diagnosticSink, chunk, targetLanguageLevel)
      if (forkSdk == null) {
        return false
      }
    }
    else {
      forkSdk = null
    }

    val compilerSdkVersion = (if (forkSdk == null) JavaVersion.current().feature else forkSdk.getSecond())!!

    val vm_compilerOptions = getCompilationOptions(compilerSdkVersion, context, chunk, profile, compilingTool)
    val vmOptions = vm_compilerOptions.first
    val options = vm_compilerOptions.second

    val effectivePlatformCp = calcEffectivePlatformCp(originalPlatformCp, options, compilingTool)
    if (effectivePlatformCp == null) {
      val text = JpsBuildBundle.message(
        "build.message.unsupported.compact.compilation.profile.was.requested", chunk.name, System.getProperty("java.version")
      )
      context.processMessage(CompilerMessage(builderName, BuildMessage.Kind.ERROR, text))
      return false
    }

    val platformCp: Iterable<File>?
    val classPath: Iterable<File>?
    val modulePath: ModulePath
    val upgradeModulePath: Iterable<File>?
    if (moduleInfoFile != null) { // has modules
      val splitter = MODULE_PATH_SPLITTER.get(context)
      val pair = splitter.splitPath(
        moduleInfoFile, outs.keys, ProjectPaths.getCompilationModulePath(chunk, false), collectAdditionalRequires(options)
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
        classPath = mutableListOf<File>()
      }
      else {
        // placing only explicitly referenced modules into the module path and the rest of deps to classpath
        modulePath = pair.first
        classPath = pair.second
      }
      // modules above the JDK in the order entry list make a module upgrade path
      upgradeModulePath = effectivePlatformCp
      platformCp = emptyList<File>()
    }
    else {
      modulePath = ModulePath.EMPTY
      upgradeModulePath = mutableListOf<File>()
      if (!effectivePlatformCp.iterator().hasNext() && getChunkSdkVersion(chunk) >= 9) {
        // If chunk's SDK is 9 or higher, there is no way to specify full platform classpath
        // because platform classes are stored in `jimage` binary files with unknown format.
        // Because of this, we are clearing platform classpath so that javac will resolve against its own boot classpath
        // and prepending additional jars from the JDK configuration to compilation classpath
        platformCp = emptyList<File>()
        classPath = Iterators.flat(effectivePlatformCp, originalClassPath)
      }
      else {
        platformCp = effectivePlatformCp
        classPath = originalClassPath
      }
    }

    val classesConsumer = ClassProcessingConsumer(context, outputSink)

    if (forkSdk != null) {
      updateCompilerUsageStatistics(context, "javac " + forkSdk.getSecond(), chunk)
      val server = ensureJavacServerStarted(context)
      val paths = CompilationPaths.create(platformCp, classPath, upgradeModulePath, modulePath, sourcePath)
      val heapSize = Utils.suggestForkedCompilerHeapSize()
      return invokeJavac(
        compilerSdkVersion,
        context,
        chunk,
        compilingTool,
        options,
        files,
        classesConsumer
      ) { options, files, outSink ->
        logJavacCall(chunk, options, "fork")
        server.forkJavac(
          forkSdk.getFirst(),
          heapSize,
          vmOptions,
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

    updateCompilerUsageStatistics(context, compilingTool.description, chunk)
    return invokeJavac(
      compilerSdkVersion,
      context,
      chunk,
      compilingTool,
      options,
      files,
      classesConsumer,
      JavacCaller { options, files, outSink ->
        logJavacCall(chunk, options, "in-process")
        JavacMain.compile(
          options,
          files,
          classPath,
          platformCp,
          modulePath,
          upgradeModulePath,
          sourcePath,
          outs,
          diagnosticSink,
          outSink,
          context.cancelStatus,
          compilingTool
        )
      }
    )
  }

  private fun interface JavacCaller {
    fun invoke(options: Iterable<String>, files: Iterable<File>?, outSink: OutputFileConsumer): kotlin.Boolean
  }

  override fun chunkBuildFinished(context: CompileContext?, chunk: ModuleChunk?) {
    JavaBuilderUtil.cleanupChunkResources(context)
    ExternalJavacManagerKey.KEY.get(context)?.shutdownIdleProcesses()
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

  private inner class ClassProcessingConsumer(private val context: CompileContext, sink: OutputFileConsumer?) : OutputFileConsumer {
    private val delegateOutputFileSink = sink ?: OutputFileConsumer { fileObject ->
      throw RuntimeException("Output sink for compiler was not specified")
    }

    override fun save(fileObject: OutputFileObject) {
      try {
        val content = fileObject.content
        val file = fileObject.file
        if (content == null) {
          context.processMessage(CompilerMessage(
            builderName, BuildMessage.Kind.WARNING, JpsBuildBundle.message("build.message.missing.content.for.file.0", file.path))
          )
        }
        else {
          saveToFile(file.toPath(), content)
        }
      }
      catch (e: IOException) {
        context.processMessage(CompilerMessage(builderName, BuildMessage.Kind.ERROR, e.message))
      }

      delegateOutputFileSink.save(fileObject)
    }
  }

  override fun getExpectedBuildTime(): Long = 100
}

private fun saveToFile(file: Path, content: BinaryContent) {
  try {
    writeToFile(file, content)
  }
  catch (e: IOException) {
    // assuming the reason is non-existing parent
    val parentFile = file.parent
    if (parentFile == null) {
      throw e
    }

    Files.createDirectories(parentFile)
    // second attempt
    writeToFile(file, content)
  }
}

private val writeOptions = EnumSet.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE)

private fun writeToFile(file: Path, content: BinaryContent) {
  FileChannel.open(file, writeOptions).use { channel ->
    val buffer = ByteBuffer.wrap(content.buffer, content.offset, content.length)
    val length = buffer.remaining()
    var toWrite = length
    var currentPosition = 0L
    while (toWrite > 0) {
      val n = channel.write(buffer, currentPosition)
      if (n < 0) {
        throw EOFException("Unexpected end of file while writing to FileChannel")
      }
      toWrite -= n
      currentPosition += n
    }
  }
}

private class OutputFilesSink(
  private val context: CompileContext,
  private val outputConsumer: OutputConsumer,
  private val mappingsCallback: Backend,
  chunkName: String
) : OutputFileConsumer {
  private val chunkOutputRootName = "$" + chunkName.replace("\\\\s".toRegex(), "_")
  @JvmField val successfullyCompiled = hashSet<File>()

  override fun save(fileObject: OutputFileObject) {
    val content = fileObject.content
    val outKind = fileObject.getKind()
    val sourceFiles = fileObject.sourceFiles.toList()
    if (!sourceFiles.isEmpty() && content != null) {
      val sourcePaths = sourceFiles.map { FileUtilRt.toSystemIndependentName(it.path) }
      var rootDescriptor: JavaSourceRootDescriptor? = null
      for (sourceFile in sourceFiles) {
        rootDescriptor = (context.projectDescriptor.buildRootIndex as BazelBuildRootIndex).fileToDescriptors.get(sourceFile.toPath())
        if (rootDescriptor != null) {
          break
        }
      }

      try {
        if (rootDescriptor == null) {
          // was not able to determine the source root descriptor, or the source root is excluded from compilation (e.g., for annotation processors)
          if (outKind == JavaFileObject.Kind.CLASS) {
            outputConsumer.registerCompiledClass(null, CompiledClass(fileObject.file, sourceFiles, fileObject.className, content))
          }
        }
        else {
          // first, handle [src->output] mapping and register paths for files_generated event
          if (outKind == JavaFileObject.Kind.CLASS) {
            outputConsumer.registerCompiledClass(rootDescriptor.target, CompiledClass(fileObject.file, sourceFiles, fileObject.className, content))
          }
          else {
            outputConsumer.registerOutputFile(rootDescriptor.target, fileObject.file, sourcePaths)
          }
        }
      }
      catch (e: IOException) {
        @Suppress("DEPRECATION")
        context.processMessage(CompilerMessage("java", e))
      }

      if (outKind == JavaFileObject.Kind.CLASS) {
        // register in mappings any non-temp class file
        try {
          val reader = FailSafeClassReader(content.buffer, content.offset, content.length)
          val fileName = if (JavaBuilderUtil.isDepGraphEnabled()) {
            chunkOutputRootName + "/" + FileUtilRt.toSystemIndependentName(fileObject.relativePath)
          }
          else {
            fileObject.file.invariantSeparatorsPath
          }
          mappingsCallback.associate(fileName, sourcePaths, reader, fileObject.isGenerated)
        }
        catch (e: Throwable) {
          // need this to make sure that unexpected errors in, for example, ASM will not ruin the compilation
          val message = JpsBuildBundle.message("build.message.class.dependency.information.may.be.incomplete", fileObject.file.path)
          LOG.info(message, e)
          for (sourcePath in sourcePaths) {
            context.processMessage(CompilerMessage(
              "java", BuildMessage.Kind.WARNING, message + "\n" + CompilerMessage.getTextFromThrowable(e), sourcePath
            ))
          }
        }
      }
    }

    if (outKind == JavaFileObject.Kind.CLASS && !sourceFiles.isEmpty()) {
      successfullyCompiled.addAll(sourceFiles)
    }
  }
}

private class DiagnosticSink(
  private val context: CompileContext,
  private val registrars: MutableCollection<JavacFileReferencesRegistrar>
) : DiagnosticOutputConsumer {
  private val myErrorCount = AtomicInteger(0)
  private val myWarningCount = AtomicInteger(0)
  private val myFilesWithErrors = hashSet<File>()

  override fun javaFileLoaded(file: File?) {
  }

  override fun registerJavacFileData(data: JavacFileData) {
    for (registrar in registrars) {
      registrar.registerFile(
        context,
        data.filePath,
        Iterators.map(data.refs.entries, Iterators.Function { it }),
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
      for (listener in JpsServiceManager.getInstance().getExtensions<CustomOutputDataListener?>(CustomOutputDataListener::class.java)) {
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

    if (line.startsWith(ExternalJavacManager.STDOUT_LINE_PREFIX)) {
      println(line)
      if (LOG.isDebugEnabled) {
        LOG.debug(line)
      }
    }
    else if (line.startsWith(ExternalJavacManager.STDERR_LINE_PREFIX)) {
      System.err.println(line)
      if (LOG.isDebugEnabled) {
        LOG.debug(line)
      }
    }
    else if (line.contains("java.lang.OutOfMemoryError")) {
      context.processMessage(CompilerMessage(builderName, BuildMessage.Kind.ERROR, JpsBuildBundle.message("build.message.insufficient.memory")))
      myErrorCount.incrementAndGet()
    }
    else {
      context.processMessage(CompilerMessage(builderName, BuildMessage.Kind.INFO, line))
    }
  }

  override fun report(diagnostic: Diagnostic<out JavaFileObject?>) {
    val kind: BuildMessage.Kind
    when (diagnostic.kind) {
      Diagnostic.Kind.ERROR -> {
        kind = BuildMessage.Kind.ERROR
        myErrorCount.incrementAndGet()
      }

      Diagnostic.Kind.MANDATORY_WARNING, Diagnostic.Kind.WARNING -> {
        kind = BuildMessage.Kind.WARNING
        myWarningCount.incrementAndGet()
      }

      Diagnostic.Kind.NOTE -> kind = BuildMessage.Kind.INFO
      Diagnostic.Kind.OTHER -> kind = if (diagnostic is JpsInfoDiagnostic) BuildMessage.Kind.JPS_INFO else BuildMessage.Kind.OTHER
      else -> kind = BuildMessage.Kind.OTHER
    }
    var sourceFile: File? = null
    try {
      // for eclipse compiler just an attempt to call getSource() may lead to an NPE,
      // so calling this method under try/catch to avoid induced compiler errors
      val source: JavaFileObject? = diagnostic.getSource()
      sourceFile = if (source != null) File(source.toUri()) else null
    }
    catch (e: Exception) {
      LOG.info(e)
    }
    val srcPath: String?
    if (sourceFile != null) {
      if (kind == BuildMessage.Kind.ERROR) {
        myFilesWithErrors.add(sourceFile)
      }
      srcPath = FileUtil.toSystemIndependentName(sourceFile.path)
    }
    else {
      srcPath = null
    }
    val message = diagnostic.getMessage(Locale.US)
    if (Utils.IS_TEST_MODE) {
      LOG.info(message)
    }
    val compilerMsg = CompilerMessage(
      builderName, kind, message, srcPath, diagnostic.startPosition,
      diagnostic.endPosition, diagnostic.position, diagnostic.lineNumber,
      diagnostic.columnNumber
    )
    if (LOG.isDebugEnabled) {
      LOG.debug(compilerMsg.toString())
    }
    context.processMessage(compilerMsg)
  }

  val errorCount: Int
    get() = myErrorCount.get()

  val warningCount: Int
    get() = myWarningCount.get()

  val filesWithErrors: Collection<File>
    get() = myFilesWithErrors
}