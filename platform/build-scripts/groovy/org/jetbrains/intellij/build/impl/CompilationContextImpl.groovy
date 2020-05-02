// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.PathUtilRt
import com.intellij.util.SystemProperties
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.compilation.CompilationPartsUtil
import org.jetbrains.intellij.build.impl.compilation.PortableCompilationCache
import org.jetbrains.intellij.build.impl.logging.BuildMessagesImpl
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.artifact.JpsArtifactService
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsOrderRootType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.util.JpsPathUtil

import java.util.concurrent.atomic.AtomicLong
import java.util.function.BiFunction

@CompileStatic
class CompilationContextImpl implements CompilationContext {
  final AntBuilder ant
  final GradleRunner gradle
  final BuildOptions options
  final BuildMessages messages
  final BuildPaths paths
  final JpsProject project
  final JpsGlobal global
  final JpsModel projectModel
  final Map<String, String> oldToNewModuleName
  final Map<String, String> newToOldModuleName
  JpsCompilationData compilationData

  @SuppressWarnings("GrUnresolvedAccess")
  @CompileDynamic
  static CompilationContextImpl create(String communityHome, String projectHome, String defaultOutputRoot) {
    //noinspection GroovyAssignabilityCheck
    return create(communityHome, projectHome,
                  { p, m -> defaultOutputRoot } as BiFunction<JpsProject, BuildMessages, String>, new BuildOptions())
   }

  static CompilationContextImpl create(String communityHome, String projectHome,
                                       BiFunction<JpsProject, BuildMessages, String> buildOutputRootEvaluator, BuildOptions options) {
    AntBuilder ant = new AntBuilder()
    def messages = BuildMessagesImpl.create(ant.project)
    communityHome = toCanonicalPath(communityHome)
    if (["platform/build-scripts", "bin/log.xml", "build.txt"].any { !new File(communityHome, it).exists() }) {
      messages.error("communityHome ($communityHome) doesn't point to a directory containing IntelliJ Community sources")
    }

    def dependenciesProjectDir = new File(communityHome, 'build/dependencies')
    logFreeDiskSpace(messages, projectHome, "before downloading dependencies")
    def gradleJdk = toCanonicalPath(JdkUtils.computeJdkHome(messages, '1.8', null, "JDK_18_x64"))
    GradleRunner gradle = new GradleRunner(dependenciesProjectDir, projectHome, messages, gradleJdk)
    if (!options.isInDevelopmentMode) {
      setupCompilationDependencies(gradle, options)
    }
    else {
      gradle.run('Setting up Kotlin plugin', 'setupKotlinPlugin')
    }

    projectHome = toCanonicalPath(projectHome)
    def kotlinHome = toCanonicalPath("$communityHome/build/dependencies/build/kotlin/Kotlin")
    def model = loadProject(projectHome, kotlinHome, messages, options, ant, gradle)
    def jdkHome = defineJavaSdk(model, projectHome, options, messages)
    def oldToNewModuleName = loadModuleRenamingHistory(projectHome, messages) + loadModuleRenamingHistory(communityHome, messages)
    def context = new CompilationContextImpl(ant, gradle, model, communityHome, projectHome, jdkHome, kotlinHome, messages, oldToNewModuleName,
                                             buildOutputRootEvaluator, options)
    context.prepareForBuild()
    messages.debugLogPath = "$context.paths.buildOutputRoot/log/debug.log"
    def jpsCache = new PortableCompilationCache(context)
    if (jpsCache.canBeUsed) jpsCache.warmUp()
    return context
  }

  private static String defineJavaSdk(JpsModel model, String projectHome, BuildOptions options, BuildMessages messages) {
    def sdks = []
    def jbrDir = jbrDir(projectHome, options)
    def jdk6Home = JdkUtils.computeJdkHome(messages, '1.6', "$jbrDir/1.6", "JDK_16_x64")
    JdkUtils.defineJdk(model.global, "IDEA jdk", jdk6Home, messages)
    sdks << "IDEA jdk"
    def jbrVersionName = jbrVersionName(options)
    sdks << jbrVersionName
    def jbrDefaultDir = "$jbrDir/$jbrVersionName"
    def jbrEnvVar = "JDK_${options.jbrVersion < 9 ? "1$options.jbrVersion" : options.jbrVersion}_x64"
    def jbrHome = toCanonicalPath(JdkUtils.computeJdkHome(messages, jbrVersionName, jbrDefaultDir, jbrEnvVar))
    JdkUtils.defineJdk(model.global, jbrVersionName, jbrHome, messages)
    readModulesFromReleaseFile(model, jbrVersionName, jbrHome)
    model.project.modules
      .collect { it.getSdkReference(JpsJavaSdkType.INSTANCE)?.sdkName }
      .findAll { it != null && !sdks.contains(it) }
      .toSet().each { sdkName ->
      def sdkHome = JdkUtils.computeJdkHome(messages, sdkName, "$jbrDir/$sdkName", null)?.with {
        toCanonicalPath(it)
      }
      if (sdkHome != null) {
        JdkUtils.defineJdk(model.global, sdkName, sdkHome, messages)
        readModulesFromReleaseFile(model, sdkName, sdkHome)
      }
      else {
        messages.warning("JDK $sdkName is required to compile the project but it's not found")
      }
    }
    return jbrHome
  }

  private static def readModulesFromReleaseFile(JpsModel model, String sdkName, String sdkHome) {
    def additionalSdk = model.global.libraryCollection.findLibrary(sdkName)
    def urls = additionalSdk.getRoots(JpsOrderRootType.COMPILED).collect { it.url }
    JdkUtils.readModulesFromReleaseFile(new File(sdkHome)).each {
      if (!urls.contains(it)) {
        additionalSdk.addRoot(it, JpsOrderRootType.COMPILED)
      }
    }
  }

  private static String jbrDir(String projectHome, BuildOptions options) {
    options.jdksTargetDir?.with {
      new File(it).exists() ? it : null
    } ?: "$projectHome/build/jdk"
  }

  private static String jbrVersionName(BuildOptions options) {
    "${options.jbrVersion < 9 ? "1.$options.jbrVersion" : options.jbrVersion}"
  }

  @SuppressWarnings(["GrUnresolvedAccess", "GroovyAssignabilityCheck"])
  @CompileDynamic
  static Map<String, String> loadModuleRenamingHistory(String projectHome, BuildMessages messages) {
    def modulesXml = new File(projectHome, ".idea/modules.xml")
    if (!modulesXml.exists()) {
      messages.error("Incorrect project home: $modulesXml doesn't exist")
    }
    def root = new XmlParser().parse(modulesXml)
    def renamingHistoryTag = root.component.find { it.@name == "ModuleRenamingHistory"}
    def mapping = new LinkedHashMap<String, String>()
    renamingHistoryTag?.module?.each { mapping[it.'@old-name'] = it.'@new-name' }
    return mapping
  }

  private CompilationContextImpl(AntBuilder ant, GradleRunner gradle, JpsModel model, String communityHome,
                                 String projectHome, String jdkHome, String kotlinHome, BuildMessages messages,
                                 Map<String, String> oldToNewModuleName,
                                 BiFunction<JpsProject, BuildMessages, String> buildOutputRootEvaluator, BuildOptions options) {
    this.ant = ant
    this.gradle = gradle
    this.projectModel = model
    this.project = model.project
    this.global = model.global
    this.options = options
    this.messages = messages
    this.oldToNewModuleName = oldToNewModuleName
    this.newToOldModuleName = oldToNewModuleName.collectEntries { oldName, newName -> [newName, oldName] } as Map<String, String>
    String buildOutputRoot = options.outputRootPath ?: buildOutputRootEvaluator.apply(project, messages)
    this.paths = new BuildPathsImpl(communityHome, projectHome, buildOutputRoot, jdkHome, kotlinHome)
  }

  CompilationContextImpl createCopy(AntBuilder ant, BuildMessages messages, BuildOptions options,
                                    BiFunction<JpsProject, BuildMessages, String> buildOutputRootEvaluator) {
    return new CompilationContextImpl(ant, gradle, projectModel, paths.communityHome, paths.projectHome, paths.jdkHome,
                                      paths.kotlinHome, messages, oldToNewModuleName, buildOutputRootEvaluator, options)
  }

  private static JpsModel loadProject(String projectHome, String kotlinHome, BuildMessages messages, BuildOptions options, AntBuilder ant, GradleRunner gradle) {
    if (!options.useCompiledClassesFromProjectOutput && options.pathToCompiledClassesArchive == null && options.pathToCompiledClassesArchivesMetadata == null) {
      //we need to add Kotlin JPS plugin to classpath before loading the project to ensure that Kotlin settings will be properly loaded
      ensureKotlinJpsPluginIsAddedToClassPath(kotlinHome, ant, messages)
    }

    def model = JpsElementFactory.instance.createModel()
    def pathVariablesConfiguration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)
    pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", FileUtil.toSystemIndependentName(new File(SystemProperties.getUserHome(), ".m2/repository").absolutePath))

    def pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    JpsProjectLoader.loadProject(model.project, pathVariables, projectHome)
    messages.info("Loaded project $projectHome: ${model.project.modules.size()} modules, ${model.project.libraryCollection.libraries.size()} libraries")
    model
  }

  static boolean dependenciesInstalled
  static void setupCompilationDependencies(GradleRunner gradle, BuildOptions options) {
    if (!dependenciesInstalled) {
      dependenciesInstalled = true
      String[] args = ['setupJdks', 'setupKotlinPlugin']
      if (options.jdksTargetDir != null) args += "-D$BuildOptions.JDKS_TARGET_DIR_OPTION=$options.jdksTargetDir".toString()
      gradle.run('Setting up compilation dependencies', args)
    }
  }

  private static void ensureKotlinJpsPluginIsAddedToClassPath(String kotlinHomePath, AntBuilder ant, BuildMessages messages) {
    if (CompilationContextImpl.class.getResource("/org/jetbrains/kotlin/jps/build/KotlinBuilder.class") != null) {
      return
    }

    def kotlinPluginLibPath = "$kotlinHomePath/lib"
    def kotlincLibPath = "$kotlinHomePath/kotlinc/lib"
    if (new File(kotlinPluginLibPath).exists() && new File(kotlincLibPath).exists()) {
      ["jps/kotlin-jps-plugin.jar", "kotlin-plugin.jar", "kotlin-reflect.jar"].each {
        BuildUtils.addToJpsClassPath("$kotlinPluginLibPath/$it", ant)
      }
      ["kotlin-stdlib.jar"].each {
        BuildUtils.addToJpsClassPath("$kotlincLibPath/$it", ant)
      }
    }
    else {
      messages.error(
        "Could not find Kotlin JARs at $kotlinPluginLibPath and $kotlincLibPath: run `./gradlew setupKotlinPlugin` in dependencies module to download Kotlin JARs")
    }
  }

  void prepareForBuild() {
    checkCompilationOptions()
    def dataDirName = ".jps-build-data"
    def logDir = new File(paths.buildOutputRoot, "log")
    FileUtil.delete(logDir)
    compilationData = new JpsCompilationData(new File(paths.buildOutputRoot, dataDirName), new File("$logDir/compilation.log"),
                                             System.getProperty("intellij.build.debug.logging.categories", ""), messages)

    def classesDirName = "classes"
    def projectArtifactsDirName = "project-artifacts"
    def classesOutput = "$paths.buildOutputRoot/$classesDirName"
    List<String> outputDirectoriesToKeep = ["log"]
    if (options.pathToCompiledClassesArchivesMetadata != null) {
      fetchAndUnpackCompiledClasses(messages, classesOutput, options)
      outputDirectoriesToKeep.add(classesDirName)
    }
    else if (options.pathToCompiledClassesArchive != null) {
      unpackCompiledClasses(messages, ant, classesOutput, options)
      outputDirectoriesToKeep.add(classesDirName)
    }

    String baseArtifactsOutput = "$paths.buildOutputRoot/$projectArtifactsDirName"
    JpsArtifactService.instance.getArtifacts(project).each {
      it.outputPath = "$baseArtifactsOutput/${PathUtilRt.getFileName(it.outputPath)}"
    }

    messages.info("Incremental compilation: " + options.incrementalCompilation)
    if (options.incrementalCompilation) {
      System.setProperty("kotlin.incremental.compilation", "true")
      outputDirectoriesToKeep.add(dataDirName)
      outputDirectoriesToKeep.add(classesDirName)
      outputDirectoriesToKeep.add(projectArtifactsDirName)
    }
    if (!options.useCompiledClassesFromProjectOutput) {
      projectOutputDirectory = classesOutput
    }
    else {
      def outputDir = getProjectOutputDirectory()
      if (!outputDir.exists()) {
        messages.error("$BuildOptions.USE_COMPILED_CLASSES_PROPERTY is enabled, but the project output directory $outputDir.absolutePath doesn't exist")
      }
    }

    suppressWarnings(project)
    exportModuleOutputProperties()
    cleanOutput(outputDirectoriesToKeep)
  }

  /**
   * @return url attribute value of output tag from .idea/misc.xml
   */
  File getProjectOutputDirectory() {
    JpsPathUtil.urlToFile(JpsJavaExtensionService.instance.getOrCreateProjectExtension(project).outputUrl)
  }

  void setProjectOutputDirectory(String outputDirectory) {
    String url = "file://${FileUtil.toSystemIndependentName(outputDirectory)}"
    JpsJavaExtensionService.instance.getOrCreateProjectExtension(project).outputUrl = url
  }


  void exportModuleOutputProperties() {
    for (JpsModule module : project.modules) {
      for (boolean test : [true, false]) {
        [module.name, getOldModuleName(module.name)].findAll { it != null}.each {
          ant.project.setProperty("module.${it}.output.${test ? "test" : "main"}", getOutputPath(module, test))
        }
      }
    }
  }

  void cleanOutput(List<String> outputDirectoriesToKeep) {
    messages.block("Clean output") {
      def outputPath = paths.buildOutputRoot
      messages.progress("Cleaning output directory $outputPath")
      new File(outputPath).listFiles()?.each { File file ->
        if (outputDirectoriesToKeep.contains(file.name)) {
          messages.info("Skipped cleaning for $file.absolutePath")
        }
        else {
          messages.info("Deleting $file.absolutePath")
          FileUtil.delete(file)
        }
      }
    }
  }

  @CompileDynamic
  private static void unpackCompiledClasses(BuildMessages messages, AntBuilder ant, String classesOutput, BuildOptions options) {
    messages.block("Unpack compiled classes archive") {
      FileUtil.delete(new File(classesOutput))
      ant.unzip(src: options.pathToCompiledClassesArchive, dest: classesOutput)
    }
  }

  @CompileStatic
  private static void fetchAndUnpackCompiledClasses(BuildMessages messages, String classesOutput, BuildOptions options) {
    CompilationPartsUtil.fetchAndUnpackCompiledClasses(messages, classesOutput, options)
  }

  private void checkCompilationOptions() {
    if (options.useCompiledClassesFromProjectOutput && options.incrementalCompilation) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, so the archive with compiled project output won't be used")
      options.pathToCompiledClassesArchive = null
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output metadata is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, so the archive with the compiled project output metadata won't be used to fetch compile output")
      options.pathToCompiledClassesArchivesMetadata = null
    }
    if (options.incrementalCompilation && "false" == System.getProperty("teamcity.build.branch.is_default")) {
      messages.warning("Incremental builds for feature branches have no sense because JPS caches are out of date, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
  }

  private static void suppressWarnings(JpsProject project) {
    def compilerOptions = JpsJavaExtensionService.instance.getOrCreateCompilerConfiguration(project).currentCompilerOptions
    compilerOptions.GENERATE_NO_WARNINGS = true
    compilerOptions.DEPRECATION = false
    compilerOptions.ADDITIONAL_OPTIONS_STRING = compilerOptions.ADDITIONAL_OPTIONS_STRING.replace("-Xlint:unchecked", "")
  }

  @Override
  JpsModule findRequiredModule(String name) {
    def module = findModule(name)
    if (module == null) {
      messages.error("Cannot find required module '$name' in the project")
    }
    return module
  }

  JpsModule findModule(String name) {
    String actualName
    if (oldToNewModuleName.containsKey(name)) {
      actualName = oldToNewModuleName[name]
      messages.warning("Old module name '$name' is used in the build scripts; use the new name '$actualName' instead")
    }
    else {
      actualName = name
    }
    project.modules.find { it.name == actualName }
  }

  @Override
  String getOldModuleName(String newName) {
    return newToOldModuleName[newName]
  }

  @Override
  String getModuleOutputPath(JpsModule module) {
    getOutputPath(module, false)
  }

  @Override
  String getModuleTestsOutputPath(JpsModule module) {
    getOutputPath(module, true)
  }

  private String getOutputPath(JpsModule module, boolean forTests) {
    File outputDirectory = JpsJavaExtensionService.instance.getOutputDirectory(module, forTests)
    if (outputDirectory == null) {
      messages.error("Output directory for '$module.name' isn't set")
    }
    return outputDirectory.absolutePath
  }

  @Override
  List<String> getModuleRuntimeClasspath(JpsModule module, boolean forTests) {
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService
      .dependencies(module).recursively()
      // if project requires different SDKs they all shouldn't be added to test classpath
      .with { forTests ? withoutSdk() : it }
      .includedIn(JpsJavaClasspathKind.runtime(forTests))
    return enumerator.classes().roots.collect { it.absolutePath }
  }

  private static final AtomicLong totalSizeOfProducedArtifacts = new AtomicLong()
  @Override
  void notifyArtifactBuilt(String artifactPath) {
    if (options.buildStepsToSkip.contains(BuildOptions.TEAMCITY_ARTIFACTS_PUBLICATION)) {
      return
    }
    def file = new File(artifactPath)
    def artifactsDir = new File(paths.artifacts)

    if (file.isFile()) {
      //temporary workaround until TW-54541 is fixed: if build is going to produce big artifacts and we have lack of free disk space it's better not to send 'artifactBuilt' message to avoid "No space left on device" errors
      def fileSize = file.size()
      if (fileSize > 1000000) {
        def producedSize = totalSizeOfProducedArtifacts.addAndGet(fileSize)
        def willBePublishedWhenBuildFinishes = FileUtil.isAncestor(artifactsDir, file, true)

        long oneGb = 1024L * 1024 * 1024
        long requiredAdditionalSpace = oneGb * 6
        long requiredSpaceForArtifacts = oneGb * 9
        long availableSpace = file.freeSpace
        //heuristics: a build publishes at most 9Gb of artifacts and requires some additional space for compiled classes, dependencies, temp files, etc.
        // So we'll publish an artifact earlier only if there will be enough space for its copy.
        def skipPublishing = willBePublishedWhenBuildFinishes && availableSpace < (requiredSpaceForArtifacts - producedSize) + requiredAdditionalSpace + fileSize
        messages.debug("Checking free space before publishing $artifactPath (${StringUtil.formatFileSize(fileSize)}): ")
        messages.debug(" total produced: ${StringUtil.formatFileSize(producedSize)}")
        messages.debug(" available space: ${StringUtil.formatFileSize(availableSpace)}")
        messages.debug(" ${skipPublishing ? "will be" : "won't be"} skipped")
        if (skipPublishing) {
          messages.info("Artifact $artifactPath won't be published early to avoid caching on agent (workaround for TW-54541)")
          return
        }
      }
    }

    def pathToReport = file.absolutePath

    def targetDirectoryPath = ""
    if (FileUtil.isAncestor(artifactsDir, file.parentFile, true)) {
      targetDirectoryPath = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(artifactsDir, file.parentFile) ?: "")
    }

    if (file.isDirectory()) {
      targetDirectoryPath = (targetDirectoryPath ? targetDirectoryPath + "/"  : "") + file.name
    }
    if (targetDirectoryPath) {
      pathToReport += "=>" + targetDirectoryPath
    }
    messages.artifactBuilt(pathToReport)
  }

  private static String toCanonicalPath(String path) {
    FileUtil.toSystemIndependentName(new File(path).canonicalPath)
  }

  static void logFreeDiskSpace(BuildMessages buildMessages, String directoryPath, String phase) {
    def dir = new File(directoryPath)
    buildMessages.debug("Free disk space $phase: ${StringUtil.formatFileSize(dir.freeSpace)} (on disk containing $dir)")
  }
}

@CompileStatic
class BuildPathsImpl extends BuildPaths {
  BuildPathsImpl(String communityHome, String projectHome, String buildOutputRoot, String jdkHome, String kotlinHome) {
    this.communityHome = communityHome
    this.projectHome = projectHome
    this.buildOutputRoot = buildOutputRoot
    this.jdkHome = jdkHome
    this.kotlinHome = kotlinHome
    artifacts = "$buildOutputRoot/artifacts"
    distAll = "$buildOutputRoot/dist.all"
    temp = "$buildOutputRoot/temp"
  }
}
