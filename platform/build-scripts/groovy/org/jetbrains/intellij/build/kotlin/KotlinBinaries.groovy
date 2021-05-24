// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.kotlin

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.xmlb.XmlSerializer
import groovy.transform.CompileStatic
import org.apache.tools.ant.BuildException
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.GradleRunner
import org.jetbrains.intellij.build.impl.BuildUtils
import org.jetbrains.intellij.build.impl.CompilationContextImpl
import org.jetbrains.intellij.build.impl.CompilationTasksImpl
import org.jetbrains.jps.model.serialization.JpsLoaderBase
import org.jetbrains.jps.model.serialization.JpsMacroExpander
import org.jetbrains.jps.model.serialization.PathMacroUtil
import org.jetbrains.jps.model.serialization.artifact.ArtifactState

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Sets up both Kotlin compiler (downloaded from Marketplace) and Kotlin plugin (built from source) binaries
 */
@CompileStatic
final class KotlinBinaries {
  static final String SET_UP_COMPILER_GRADLE_TASK = 'setupKotlinCompiler'
  private static final String PLUGIN_ARTIFACT_NAME = 'KotlinPlugin'
  private final String projectHome
  private final String communityHome
  private final BuildMessages messages
  private final BuildOptions options
  final String compilerHome

  KotlinBinaries(String projectHome, String communityHome, BuildOptions options, BuildMessages messages) {
    this.options = options
    this.messages = messages
    this.communityHome = communityHome
    this.projectHome = projectHome
    def compilerHome = "$communityHome/build/dependencies/build/kotlin-compiler/Kotlin"
    this.compilerHome = FileUtil.toSystemIndependentName(new File(compilerHome).canonicalPath)
  }

  /**
   * we need to add Kotlin JPS plugin to classpath before loading the project to ensure that Kotlin settings will be properly loaded
   */
  private void ensureKotlinJpsPluginIsAddedToClassPath(AntBuilder ant) {
    if (KotlinBinaries.class.getResource("/org/jetbrains/kotlin/jps/build/KotlinBuilder.class") != null) {
      return
    }

    def kotlinPluginLibPath = "$compilerHome/lib"
    def kotlincLibPath = "$compilerHome/kotlinc/lib"
    if (new File(kotlinPluginLibPath).exists() && new File(kotlincLibPath).exists()) {
      ["jps/kotlin-jps-plugin.jar", "kotlin-plugin.jar", "kotlin-reflect.jar", "kotlin-common.jar"].each {
        def completePath = "$kotlinPluginLibPath/$it"
        if (new File(completePath).exists()) {
          BuildUtils.addToJpsClassPath(completePath, ant)
        }
      }
      ["kotlin-stdlib.jar"].each {
        BuildUtils.addToJpsClassPath("$kotlincLibPath/$it", ant)
      }
    }
    else {
      messages.error(
        "Could not find Kotlin JARs at $kotlinPluginLibPath and $kotlincLibPath: run `./gradlew $SET_UP_COMPILER_GRADLE_TASK` in dependencies module to download Kotlin JARs"
      )
    }
  }

  boolean isCompilerRequired() {
    return !CompilationTasksImpl.areCompiledClassesProvided(options)
  }

  void setUpCompilerIfRequired(GradleRunner gradle, AntBuilder ant) {
    if (!options.skipDependencySetup) {
      def isCompilerRequired = isCompilerRequired()
      if (!options.isInDevelopmentMode) {
        CompilationContextImpl.setupCompilationDependencies(gradle, options, isCompilerRequired)
      }
      else if (isCompilerRequired) {
        gradle.run('Setting up Kotlin compiler', KotlinBinaries.SET_UP_COMPILER_GRADLE_TASK)
      }
      if (isCompilerRequired) {
        ensureKotlinJpsPluginIsAddedToClassPath(ant)
      }
    }
  }

  private volatile Path pluginArtifact

  Path setUpPlugin(CompilationContext context, boolean isTestBuild = context.options.isTestBuild) {
    if (pluginArtifact == null) {
      synchronized (this) {
        if (pluginArtifact != null) return pluginArtifact
        if (context.options.prebuiltKotlinPluginPath != null) {
          pluginArtifact = Paths.get(context.options.prebuiltKotlinPluginPath)
        }
        else {
          pluginArtifact = buildPluginForTests(context)
          if (!isTestBuild) {
            def problem = "Prebuilt Kotlin plugin artifact is required to build an installer, " +
                          "please specify it in $BuildOptions.PREBUILT_KOTLIN_PLUGIN_PATH"
            println("##teamcity[buildProblem description='$problem']")
          }
        }
        copyPluginArtifactToProjectOutput()
      }
    }
    return pluginArtifact
  }

  private static Path buildPluginForTests(CompilationContext context) {
    context.messages.block("Building Kotlin plugin for tests") {
      buildPluginForTests(context, KotlinPluginKind.IJ, PLUGIN_ARTIFACT_NAME)
        ?: buildPluginForTests(context, KotlinPluginKind.IJ_CE, "${PLUGIN_ARTIFACT_NAME}Community")
    }
  }

  private static Path buildPluginForTests(CompilationContext context, KotlinPluginKind kind, String unzippedPath) {
    def pluginZip = kind.build(context)
    if (pluginZip == null) return null
    def pluginUnzipped = Paths.get(pluginZip.outputPath)
      .parent.resolve(unzippedPath)
      .toAbsolutePath().normalize()
    return pluginUnzipped
  }

  /**
   * Copy artifact to path expected at least by:
   * * .idea/libraries/KotlinPlugin.xml;
   * * startup performance tests;
   * * Code With Me tests;
   * * Fleet tests.
   */
  private void copyPluginArtifactToProjectOutput() {
    def stream = Files.walk(pluginArtifact)
    try {
      stream.forEach {
        if (Files.isRegularFile(it)) {
          def destination = projectOutputPath().resolve(pluginArtifact.relativize(it))
          Files.createDirectories(destination.parent)
          Files.copy(it, destination, StandardCopyOption.REPLACE_EXISTING)
        }
      }
    }
    finally {
      stream.close()
    }
  }

  private Path projectOutputPath() {
    def projectPath = Paths.get(projectHome)
    def pluginArtifactXml = projectPath.resolve(".idea/artifacts/${PLUGIN_ARTIFACT_NAME}.xml")
    if (!Files.exists(pluginArtifactXml)) {
      pluginArtifactXml = projectPath.resolve(".idea/artifacts/${PLUGIN_ARTIFACT_NAME}Community.xml")
    }
    if (!Files.exists(pluginArtifactXml)) {
      throw new BuildException("$pluginArtifactXml doesn't exist")
    }
    def rootElement = JpsLoaderBase.tryLoadRootElement(pluginArtifactXml)
    def artifactElement = JDOMUtil.getChildren(rootElement, "artifact").first()
    def state = XmlSerializer.deserialize(artifactElement, ArtifactState.class)
    def macroExpander = new JpsMacroExpander([:])
    macroExpander.addFileHierarchyReplacements(PathMacroUtil.PROJECT_DIR_MACRO_NAME, projectPath.toFile())
    return Paths.get(macroExpander.substitute(state.outputPath, SystemInfo.isFileSystemCaseSensitive))
  }
}
