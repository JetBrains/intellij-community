// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.text.Formats
import com.intellij.util.PathUtilRt
import com.intellij.util.SystemProperties
import groovy.lang.Closure
import groovy.lang.GString
import groovy.util.Node
import groovy.util.XmlParser
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.StringGroovyMethods
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.CompilationTasks.Companion.create
import org.jetbrains.intellij.build.ConsoleSpanExporter.Companion.setPathRoot
import org.jetbrains.intellij.build.TracerProviderManager.flush
import org.jetbrains.intellij.build.TracerProviderManager.setOutput
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.Jdk11Downloader
import org.jetbrains.intellij.build.impl.JdkUtils.defineJdk
import org.jetbrains.intellij.build.impl.JdkUtils.readModulesFromReleaseFile
import org.jetbrains.intellij.build.impl.logging.BuildMessagesHandler
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.jetbrains.intellij.build.kotlin.KotlinBinaries
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.artifact.JpsArtifact
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsLibraryRoot
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.util.JpsPathUtil
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.function.BiFunction

class CompilationContextImpl : CompilationContext {
  private constructor(model: JpsModel,
                      communityHome: Path,
                      projectHome: Path,
                      messages: BuildMessages,
                      oldToNewModuleName: Map<String, String>,
                      buildOutputRootEvaluator: BiFunction<JpsProject, BuildMessages, String>,
                      options: BuildOptions) {
    projectModel = model
    project = model.project
    global = model.global
    this.options = options
    this.messages = messages
    this.oldToNewModuleName = oldToNewModuleName
    newToOldModuleName = DefaultGroovyMethods.asType<Map<*, *>>(
      DefaultGroovyMethods.collectEntries<Any?, Any?, String, String>(oldToNewModuleName, object : Closure<List<String?>?>(this, this) {
        fun doCall(oldName: Any?, newName: Any?): List<String> {
          return ArrayList<String>(Arrays.asList(newName, oldName))
        }
      }), MutableMap::class.java)
    val modules = model.project.modules
    val nameToModule = arrayOfNulls<Map.Entry<String, JpsModule>?>(modules.size)
    val i = 0
    while (true) {
      i < modules.size
    }
    run {
      val module = modules[i]
      nameToModule[i] = java.util.Map.entry(module.name, module)
    }
    this.nameToModule = java.util.Map.ofEntries(*nameToModule)
    val path = options.outputRootPath
    val buildOutputRoot = if (StringGroovyMethods.asBoolean(path)) path else buildOutputRootEvaluator.apply(project, messages)
    val logDir = if (options.logPath != null) Path.of(options.logPath) else Path.of(buildOutputRoot, "log")
    paths = BuildPathsImpl(communityHome, projectHome, buildOutputRoot, logDir)
    dependenciesProperties = DependenciesProperties(this)
    bundledRuntime = BundledRuntimeImpl(this)
    stableJdkHome = Jdk11Downloader.getJdkHome(paths.buildDependenciesCommunityRoot)
    stableJavaExecutable = Jdk11Downloader.getJavaExecutable(stableJdkHome)
  }

  fun createCopy(messages: BuildMessages,
                 options: BuildOptions,
                 buildOutputRootEvaluator: BiFunction<JpsProject, BuildMessages, String>): CompilationContextImpl {
    val copy = CompilationContextImpl(projectModel, paths.communityHomeDir, paths.projectHomeDir, messages, oldToNewModuleName,
                                      buildOutputRootEvaluator, options)
    copy.compilationData = compilationData
    return copy
  }

  private constructor(messages: BuildMessages, context: CompilationContextImpl) {
    projectModel = context.projectModel
    project = context.project
    global = context.global
    options = context.options
    this.messages = messages
    oldToNewModuleName = context.oldToNewModuleName
    newToOldModuleName = context.newToOldModuleName
    nameToModule = context.nameToModule
    paths = context.paths
    compilationData = context.compilationData
    dependenciesProperties = context.dependenciesProperties
    bundledRuntime = context.bundledRuntime
    stableJavaExecutable = context.stableJavaExecutable
    stableJdkHome = context.stableJdkHome
  }

  fun cloneForContext(messages: BuildMessages): CompilationContextImpl {
    return CompilationContextImpl(messages, this)
  }

  fun prepareForBuild() {
    checkCompilationOptions()
    NioFiles.deleteRecursively(paths.logDir)
    Files.createDirectories(paths.logDir)
    compilationData = JpsCompilationData(File(paths.buildOutputRoot, ".jps-build-data"), paths.logDir.resolve("compilation.log").toFile(),
                                         System.getProperty("intellij.build.debug.logging.categories", ""))
    val projectArtifactsDirName = "project-artifacts"
    overrideProjectOutputDirectory()
    val baseArtifactsOutput = paths.buildOutputRoot + "/" + projectArtifactsDirName
    DefaultGroovyMethods.each(JpsArtifactService.getInstance().getArtifacts(project), object : Closure<GString?>(this, this) {
      @JvmOverloads
      fun doCall(it: JpsArtifact? = null): GString {
        return setOutputPath(it, "$baseArtifactsOutput/" + PathUtilRt.getFileName(
          it!!.outputPath))
      }
    })
    if (!options.useCompiledClassesFromProjectOutput) {
      messages.info("Incremental compilation: " + options.incrementalCompilation)
    }
    if (options.incrementalCompilation) {
      System.setProperty("kotlin.incremental.compilation", "true")
    }
    suppressWarnings(project)
    exportModuleOutputProperties()
    flush()
    setPathRoot(paths.buildOutputDir)
    /**
     * FIXME should be called lazily yet it breaks [TestingTasks.runTests], needs investigation
     */
    create(this).reuseCompiledClassesIfProvided()
  }

  private fun overrideProjectOutputDirectory(): GString? {
    if (options.projectClassesOutputDirectory != null && !options.projectClassesOutputDirectory!!.isEmpty()) {
      return setProjectOutputDirectory0(this, options.projectClassesOutputDirectory)
    }
    else if (options.useCompiledClassesFromProjectOutput) {
      val outputDir = projectOutputDirectory
      if (!outputDir.exists()) {
        messages.error(BuildOptions.USE_COMPILED_CLASSES_PROPERTY +
                       " is enabled, but the project output directory " + outputDir.toString() +
                       " doesn\'t exist")
      }
    }
    else {
      return setProjectOutputDirectory0(this, paths.buildOutputRoot + "/classes")
    }
  }

  override val projectOutputDirectory: File
    get() = JpsPathUtil.urlToFile(JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl)

  fun setProjectOutputDirectory(outputDirectory: String?) {
    val url = "file://" + FileUtilRt.toSystemIndependentName(
      outputDirectory!!)
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(project).outputUrl = url
  }

  fun exportModuleOutputProperties() {
    // defines Ant properties which are used by jetbrains.antlayout.datatypes.IdeaModuleBase class to locate module outputs
    // still used, please get rid of LayoutBuilder usages
    for (module in project.modules) {
      for (test in ArrayList(Arrays.asList(true, false))) {
        DefaultGroovyMethods.each(
          DefaultGroovyMethods.findAll(ArrayList(Arrays.asList(module.name, getOldModuleName(module.name))),
                                       object : Closure<Boolean?>(this, this) {
                                         @JvmOverloads
                                         fun doCall(it: String? = null): Boolean {
                                           return it != null
                                         }
                                       }), object : Closure<Void?>(this, this) {
          @JvmOverloads
          fun doCall(it: String? = null) {
            val outputPath = getOutputPath(module, test)
            if (outputPath != null) {
              LayoutBuilder.getAnt().project
                .setProperty(if (StringGroovyMethods.asBoolean(
                    "module.$it.output.$test")) "test"
                             else "main", outputPath)
            }
          }
        })
      }
    }
  }

  private fun checkCompilationOptions() {
    if (options.useCompiledClassesFromProjectOutput && options.incrementalCompilation) {
      messages.warning(
        "\'" + BuildOptions.USE_COMPILED_CLASSES_PROPERTY + "\' is specified, so \'incremental compilation\' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning(
        "\'" + BuildOptions.USE_COMPILED_CLASSES_PROPERTY + "\' is specified, so the archive with compiled project output won\'t be used")
      options.pathToCompiledClassesArchive = null
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output metadata is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning("\'" +
                       BuildOptions.USE_COMPILED_CLASSES_PROPERTY +
                       "\' is specified, so the archive with the compiled project output metadata won\'t be used to fetch compile output")
      options.pathToCompiledClassesArchivesMetadata = null
    }
    if (options.incrementalCompilation && "false" == System.getProperty("teamcity.build.branch.is_default")) {
      messages.warning(
        "Incremental builds for feature branches have no sense because JPS caches are out of date, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
  }

  override fun findRequiredModule(name: String): JpsModule {
    val module = findModule(name)
    if (module == null) {
      messages.error("Cannot find required module \'$name\' in the project")
    }
    return module!!
  }

  override fun findModule(name: String): JpsModule? {
    val actualName: String?
    if (oldToNewModuleName.containsKey(name)) {
      actualName = oldToNewModuleName[name]
      messages.warning("Old module name \'$name\' is used in the build scripts; use the new name \'$actualName\' instead")
    }
    else {
      actualName = name
    }
    return nameToModule[actualName]
  }

  override fun getOldModuleName(newName: String): String? {
    return newToOldModuleName[newName]
  }

  override fun getModuleOutputDir(module: JpsModule): Path {
    val url = JpsJavaExtensionService.getInstance().getOutputUrl(module, false)
    if (url == null) {
      messages.error("Output directory for \'" + module.name + "\' isn\'t set")
    }
    return Path.of(JpsPathUtil.urlToPath(url))
  }

  override fun getModuleTestsOutputPath(module: JpsModule): String {
    return getOutputPath(module, true)!!
  }

  private fun getOutputPath(module: JpsModule, forTests: Boolean): String? {
    val outputDirectory = JpsJavaExtensionService.getInstance().getOutputDirectory(module, forTests)
    if (outputDirectory == null) {
      messages.warning("Output directory for \'" + module.name + "\' isn\'t set")
    }
    return outputDirectory?.absolutePath
  }

  override fun getModuleRuntimeClasspath(module: JpsModule, forTests: Boolean): List<String> {
    val enumerator = DefaultGroovyMethods.with(JpsJavaExtensionService.dependencies(module).recursively(),
                                               object : Closure<JpsJavaDependenciesEnumerator?>(this, this) {
                                                 @JvmOverloads
                                                 fun doCall(
                                                   it: JpsJavaDependenciesEnumerator? = null): JpsJavaDependenciesEnumerator {
                                                   return if (forTests) withoutSdk() else it!!
                                                 }
                                               }).includedIn(JpsJavaClasspathKind.runtime(forTests))
    return DefaultGroovyMethods.collect(enumerator.classes().roots, object : Closure<String?>(this, this) {
      @JvmOverloads
      fun doCall(it: File? = null): String {
        return it!!.absolutePath
      }
    })
  }

  override fun notifyArtifactBuilt(artifactPath: String) {
    notifyArtifactWasBuilt(Path.of(artifactPath).toAbsolutePath().normalize())
  }

  override fun notifyArtifactWasBuilt(file: Path) {
    if (options.buildStepsToSkip.contains(BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION_STEP)) {
      return
    }
    val isRegularFile = Files.isRegularFile(file)
    var targetDirectoryPath = ""
    if (file.parent.startsWith(paths.artifactDir)) {
      targetDirectoryPath = FileUtilRt.toSystemIndependentName(paths.artifactDir.relativize(file.parent).toString())
    }
    if (!isRegularFile) {
      targetDirectoryPath = (if (StringGroovyMethods.asBoolean(targetDirectoryPath)) "$targetDirectoryPath/" else "") + file.fileName
    }
    var pathToReport = file.toString()
    if (StringGroovyMethods.asBoolean(targetDirectoryPath)) {
      pathToReport += "=>$targetDirectoryPath"
    }
    messages.artifactBuilt(pathToReport)
  }

  override val options: BuildOptions
  override val messages: BuildMessages
  override val paths: BuildPaths
  override val project: JpsProject
  val global: JpsGlobal
  override val projectModel: JpsModel
  val oldToNewModuleName: Map<String, String>
  val newToOldModuleName: Map<String, String>
  val nameToModule: Map<String?, JpsModule>
  override val dependenciesProperties: DependenciesProperties
  override val bundledRuntime: BundledRuntime
  override var compilationData: JpsCompilationData? = null
  override val stableJavaExecutable: Path
  override val stableJdkHome: Path

  companion object {
    fun create(communityHome: Path, projectHome: Path, defaultOutputRoot: String): CompilationContextImpl {
      return create(communityHome, projectHome, DefaultGroovyMethods.asType(object : Closure<String?>(null, null) {
        fun doCall(p: Any?, m: Any?): String {
          return defaultOutputRoot
        }
      }, BiFunction::class.java as Class<T?>), BuildOptions())
    }

    fun create(communityHome: Path,
               projectHome: Path,
               buildOutputRootEvaluator: BiFunction<JpsProject, BuildMessages, String>,
               options: BuildOptions): CompilationContextImpl {
      // This is not a proper place to initialize tracker for downloader
      // but this is the only place which is called in most build scripts
      BuildDependenciesDownloader.TRACER = BuildDependenciesOpenTelemetryTracer.INSTANCE
      val messages = BuildMessagesImpl.create()
      if (DefaultGroovyMethods.any(ArrayList(Arrays.asList("platform/build-scripts", "bin/idea.properties", "build.txt")),
                                   object : Closure<Any?>(null, null) {
                                     @JvmOverloads
                                     fun doCall(it: String? = null): Any {
                                       return !Files.exists(communityHome.resolve(it))
                                     }
                                   })) {
        messages.error(
          "communityHome ($communityHome) doesn\'t point to a directory containing IntelliJ Community sources")
      }
      printEnvironmentDebugInfo()
      logFreeDiskSpace(messages, projectHome, "before downloading dependencies")
      val kotlinBinaries = KotlinBinaries(communityHome, options, messages)
      val model = loadProject(projectHome, kotlinBinaries, messages)
      val oldToNewModuleName = DefaultGroovyMethods.plus(loadModuleRenamingHistory(projectHome, messages),
                                                         loadModuleRenamingHistory(communityHome, messages))
      val context = CompilationContextImpl(model, communityHome, projectHome, messages, oldToNewModuleName, buildOutputRootEvaluator,
                                           options)
      defineJavaSdk(context)
      context.prepareForBuild()

      // not as part of prepareForBuild because prepareForBuild may be called several times per each product or another flavor
      // (see createCopyForProduct)
      setOutput(context.paths.logDir.resolve("trace.json"))
      messages.setDebugLogPath(context.paths.logDir.resolve("debug.log"))

      // This is not a proper place to initialize logging
      // but this is the only place which is called in most build scripts
      BuildMessagesHandler.initLogging(messages)
      return context
    }

    private fun defineJavaSdk(context: CompilationContext) {
      val homePath = Jdk11Downloader.getJdkHome(context.paths.buildDependenciesCommunityRoot)
      val jbrHome = toCanonicalPath(homePath.toString())
      val jbrVersionName = "11"
      defineJdk(context.projectModel.global, jbrVersionName, jbrHome, context.messages)
      readModulesFromReleaseFile(context.projectModel, jbrVersionName, jbrHome)
      DefaultGroovyMethods.each(DefaultGroovyMethods.toSet(DefaultGroovyMethods.findAll(
        DefaultGroovyMethods.collect(context.projectModel.project.modules, object : Closure<String?>(null, null) {
          @JvmOverloads
          fun doCall(it: JpsModule? = null): String? {
            val reference = it!!.getSdkReference(JpsJavaSdkType.INSTANCE)
            return reference?.sdkName
          }
        }), object : Closure<Boolean?>(null, null) {
        @JvmOverloads
        fun doCall(it: String? = null): Boolean {
          return it != null
        }
      })), object : Closure<List<String?>?>(null, null) {
        fun doCall(sdkName: Any): List<String> {
          val vendorPrefixEnd = (sdkName as String).indexOf("-")
          val sdkNameWithoutVendor: String = if (vendorPrefixEnd as String != -1) sdkName.substring(vendorPrefixEnd + 1) else sdkName
          check(sdkNameWithoutVendor == "11") {
            "Project model at " + context.paths.projectHomeDir.toString() +
            " requested SDK " +
            sdkNameWithoutVendor +
            ", but only \'11\' is supported as SDK in intellij project"
          }
          if (context.projectModel.global.libraryCollection.findLibrary(sdkName) == null) {
            defineJdk(context.projectModel.global, sdkName, jbrHome, context.messages)
            return readModulesFromReleaseFile(context.projectModel, sdkName, jbrHome)
          }
        }
      })
    }

    private fun readModulesFromReleaseFile(model: JpsModel, sdkName: String, sdkHome: String): List<String> {
      val additionalSdk = model.global.libraryCollection.findLibrary(sdkName)
                          ?: throw IllegalStateException("Sdk '$sdkName' is not found")
      val urls = DefaultGroovyMethods.collect(additionalSdk.getRoots(JpsOrderRootType.COMPILED), object : Closure<String?>(null, null) {
        @JvmOverloads
        fun doCall(it: JpsLibraryRoot? = null): String {
          return it!!.url
        }
      })
      return DefaultGroovyMethods.each(readModulesFromReleaseFile(Path.of(sdkHome)), object : Closure<Void?>(null, null) {
        @JvmOverloads
        fun doCall(it: String? = null) {
          if (!urls.contains(it)) {
            additionalSdk.addRoot(it!!, JpsOrderRootType.COMPILED)
          }
        }
      })
    }

    private fun loadModuleRenamingHistory(projectHome: Path, messages: BuildMessages): Map<String, String> {
      val modulesXml = projectHome.resolve(".idea/modules.xml")
      if (!Files.exists(modulesXml)) {
        messages.error("Incorrect project home: $modulesXml doesn\'t exist")
      }
      return try {
        val root: Node = XmlParser().parse(stream)
        val renamingHistoryTag = DefaultGroovyMethods.find(root.component, object : Closure<Boolean?>(null, null) {
          @JvmOverloads
          fun doCall(it: Any? = null): Boolean {
            return it.name.equals("ModuleRenamingHistory")
          }
        })
        val mapping = LinkedHashMap<String, String>()
        DefaultGroovyMethods.each(renamingHistoryTag?.module, object : Closure<Any?>(null, null) {
          @JvmOverloads
          fun doCall(it: Any? = null): Any? {
            return mapping[it.]
            -name
            NO_NAME_PROVIDED() - name
          }
        })
        mapping
      }
    }

    private fun loadProject(projectHome: Path, kotlinBinaries: KotlinBinaries, messages: BuildMessages): JpsModel {
      val model = JpsElementFactory.getInstance().createModel()
      val pathVariablesConfiguration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)
      if (kotlinBinaries.isCompilerRequired) {
        val kotlinCompilerHome = kotlinBinaries.kotlinCompilerHome
        System.setProperty("jps.kotlin.home", kotlinCompilerHome.toFile().absolutePath)
        pathVariablesConfiguration.addPathVariable("KOTLIN_BUNDLED", kotlinCompilerHome.toString())
      }
      pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", FileUtilRt.toSystemIndependentName(
        File(SystemProperties.getUserHome(), ".m2/repository").absolutePath))
      val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
      JpsProjectLoader.loadProject(model.project, pathVariables, projectHome)
      messages.info("Loaded project " + projectHome.toString() +
                    ": " + model.project.modules.size.toString() +
                    " modules, " + model.project.libraryCollection.libraries.size.toString() +
                    " libraries")
      return model as JpsModel
    }

    private fun suppressWarnings(project: JpsProject) {
      val compilerOptions = JpsJavaExtensionService.getInstance().getCompilerConfiguration(project).currentCompilerOptions
      compilerOptions.GENERATE_NO_WARNINGS = true
      compilerOptions.DEPRECATION = false
      compilerOptions.ADDITIONAL_OPTIONS_STRING = compilerOptions.ADDITIONAL_OPTIONS_STRING.replace("-Xlint:unchecked", "")
    }

    private fun toCanonicalPath(path: String): String {
      return FileUtilRt.toSystemIndependentName(File(path).canonicalPath)
    }

    fun logFreeDiskSpace(buildMessages: BuildMessages, dir: Path, phase: String) {
      buildMessages.debug("Free disk space " +
                          phase +
                          ": " +
                          Formats.formatFileSize(Files.getFileStore(dir).usableSpace) +
                          " (on disk containing " + dir.toString() +
                          ")")
    }

    fun printEnvironmentDebugInfo() {
      // print it to the stdout since TeamCity will remove any sensitive fields from build log automatically
      // don't write it to debug log file!
      val env = System.getenv()
      for (key in DefaultGroovyMethods.toSorted(env.keys)) {
        DefaultGroovyMethods.println(this, "ENV " + key + " = " + env[key])
      }
      val properties = System.getProperties()
      for (propertyName in DefaultGroovyMethods.toSorted(properties.keys)) {
        DefaultGroovyMethods.println(this, "PROPERTY " + propertyName + " = " + properties[propertyName].toString())
      }
    }

    private fun <Value : String?> setOutputPath(propOwner: JpsArtifact?, outputPath: Value): Value {
      propOwner!!.outputPath = outputPath
      return outputPath
    }

    private fun <Value : String?> setProjectOutputDirectory0(propOwner: CompilationContextImpl, outputDirectory: Value): Value {
      propOwner.setProjectOutputDirectory(outputDirectory)
      return outputDirectory
    }
  }
}