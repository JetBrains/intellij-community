/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.*
import org.jetbrains.jps.gant.JpsGantProjectBuilder
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsGlobal
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaDependenciesEnumerator
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import org.jetbrains.jps.util.JpsPathUtil

import java.util.function.BiFunction
/**
 * @author nik
 */
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
  final JpsGantProjectBuilder projectBuilder
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

    GradleRunner gradle = new GradleRunner(new File(communityHome, 'build/dependencies'), messages)
    if (!options.isInDevelopmentMode) {
      setupCompilationDependencies(gradle)
    }
    else {
      gradle.run('Setting up Kotlin plugin', 'setupKotlinPlugin')
    }

    projectHome = toCanonicalPath(projectHome)
    def jdk8Home = toCanonicalPath(JdkUtils.computeJdkHome(messages, "jdk8Home", "$projectHome/build/jdk/1.8", "JDK_18_x64"))
    def kotlinHome = toCanonicalPath("$communityHome/build/dependencies/build/kotlin/Kotlin")

    def model = loadProject(projectHome, jdk8Home, kotlinHome, messages, ant)
    def context = new CompilationContextImpl(ant, gradle, model, communityHome, projectHome, jdk8Home, kotlinHome, messages,
                                             buildOutputRootEvaluator, options)
    context.prepareForBuild()
    return context
  }

  private CompilationContextImpl(AntBuilder ant, GradleRunner gradle, JpsModel model, String communityHome,
                                 String projectHome, String jdk8Home, String kotlinHome, BuildMessages messages,
                                 BiFunction<JpsProject, BuildMessages, String> buildOutputRootEvaluator, BuildOptions options) {
    this.ant = ant
    this.gradle = gradle
    this.projectModel = model
    this.project = model.project
    this.global = model.global
    this.options = options
    this.projectBuilder = new JpsGantProjectBuilder(ant.project, projectModel)
    this.messages = messages
    String buildOutputRoot = options.outputRootPath ?: buildOutputRootEvaluator.apply(project, messages)
    this.paths = new BuildPathsImpl(communityHome, projectHome, buildOutputRoot, jdk8Home, kotlinHome)
  }

  CompilationContextImpl createCopy(AntBuilder ant, BuildMessages messages, BuildOptions options,
                                    BiFunction<JpsProject, BuildMessages, String> buildOutputRootEvaluator) {
    return new CompilationContextImpl(ant, gradle, projectModel, paths.communityHome, paths.projectHome, paths.jdkHome,
                                      paths.kotlinHome, messages, buildOutputRootEvaluator, options)
  }

  private static JpsModel loadProject(String projectHome, String jdkHome, String kotlinHome, BuildMessages messages, AntBuilder ant) {
    //we need to add Kotlin JPS plugin to classpath before loading the project to ensure that Kotlin settings will be properly loaded
    ensureKotlinJpsPluginIsAddedToClassPath(kotlinHome, ant, messages)

    def model = JpsElementFactory.instance.createModel()
    JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global).addPathVariable("KOTLIN_BUNDLED", "$kotlinHome/kotlinc")

    JdkUtils.defineJdk(model.global, "IDEA jdk", JdkUtils.computeJdkHome(messages, "jdkHome", "$projectHome/build/jdk/1.6", "JDK_16_x64"))
    JdkUtils.defineJdk(model.global, "1.8", jdkHome)

    def pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    JpsProjectLoader.loadProject(model.project, pathVariables, projectHome)
    messages.info("Loaded project $projectHome: ${model.project.modules.size()} modules, ${model.project.libraryCollection.libraries.size()} libraries")
    model
  }

  static boolean dependenciesInstalled
  static void setupCompilationDependencies(GradleRunner gradle) {
    if (!dependenciesInstalled) {
      dependenciesInstalled = true
      gradle.run('Setting up compilation dependencies', 'setupJdks', 'setupKotlinPlugin')
    }
  }

  private static void ensureKotlinJpsPluginIsAddedToClassPath(String kotlinHomePath, AntBuilder ant, BuildMessages messages) {
    if (CompilationContextImpl.class.getResource("/org/jetbrains/kotlin/jps/build/KotlinBuilder.class") != null) {
      return
    }

    def kotlinPluginLibPath = "$kotlinHomePath/lib"
    if (new File(kotlinPluginLibPath).exists()) {
      ["jps/kotlin-jps-plugin.jar", "kotlin-plugin.jar", "kotlin-runtime.jar", "kotlin-reflect.jar"].each {
        BuildUtils.addToJpsClassPath("$kotlinPluginLibPath/$it", ant)
      }
    }
    else {
      messages.error(
        "Could not find Kotlin JARs at $kotlinPluginLibPath: run `./gradlew setupKotlin` in dependencies module to download Kotlin JARs")
    }
  }

  void prepareForBuild() {
    checkCompilationOptions()
    def dataDirName = options.incrementalCompilation ? ".jps-build-data-incremental" : ".jps-build-data"
    def logDir = new File(paths.buildOutputRoot, "log")
    FileUtil.delete(logDir)
    compilationData = new JpsCompilationData(new File(paths.buildOutputRoot, dataDirName), new File("$logDir/compilation.log"),
                                             System.getProperty("intellij.build.debug.logging.categories", ""), messages)

    def classesDirName = "classes"
    def classesOutput = "$paths.buildOutputRoot/$classesDirName"
    List<String> outputDirectoriesToKeep = ["log"]
    if (options.pathToCompiledClassesArchive != null) {
      unpackCompiledClasses(messages, ant, classesOutput, options)
      outputDirectoriesToKeep.add(classesDirName)
    }
    if (options.incrementalCompilation) {
      outputDirectoriesToKeep.add(dataDirName)
      outputDirectoriesToKeep.add(classesDirName)
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
        ant.project.setProperty("module.${module.name}.output.${test ? "test" : "main"}", getOutputPath(module, test))
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
    project.modules.find { it.name == name }
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
    JpsJavaDependenciesEnumerator enumerator = JpsJavaExtensionService.dependencies(module).recursively().includedIn(JpsJavaClasspathKind.runtime(forTests))
    return enumerator.classes().roots.collect { it.absolutePath }
  }


  @Override
  void notifyArtifactBuilt(String artifactPath) {
    def file = new File(artifactPath)
    def baseDir = new File(paths.projectHome)
    if (!FileUtil.isAncestor(baseDir, file, true)) {
      messages.warning("Artifact '$artifactPath' is not under '$paths.projectHome', it won't be reported")
      return
    }
    def relativePath = FileUtil.toSystemIndependentName(FileUtil.getRelativePath(baseDir, file))
    if (file.isDirectory()) {
      relativePath += "=>" + file.name
    }
    messages.artifactBuilt(relativePath)
  }

  private static String toCanonicalPath(String path) {
    FileUtil.toSystemIndependentName(new File(path).canonicalPath)
  }
}

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
