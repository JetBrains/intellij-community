/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.module.JpsModule

/**
 * We currently use JDK 1.6 and JDK 1.8 to compile modules in IntelliJ project. So if some module has Java 7 language level, we'll compile it
 * using JDK 1.8 and this class is used to check that classes from such modules actually don't use API which isn't present in JDK 1.7.
 */
@CompileStatic
class CompatibilityWithJdkChecker {
  private static final String JDK_17_ENV_VAR = "JDK_17_x64"
  private static final String PROGUARD_VERSION = "5.3.3"
  private final CompilationContext context
  private final String tempDir
  private final String jdk17Home
  private final String mavenCentralUrl

  CompatibilityWithJdkChecker(CompilationContext context) {
    this.context = context
    tempDir = "$context.paths.temp/compatibility-with-jdk-check"
    mavenCentralUrl = System.getProperty("intellij.build.maven.central.url", "http://repo1.maven.org/maven2")
    jdk17Home = System.getenv(JDK_17_ENV_VAR)
    if (jdk17Home == null) {
      context.messages.error("Failed to check compatibility with JDK: $JDK_17_ENV_VAR is not specified")
    }
    if (!new File(jdk17Home).exists()) {
      context.messages.error("Failed to check compatibility with JDK: $jdk17Home does not exist")
    }
    FileUtil.createDirectory(new File(tempDir))
  }

  void checkCompatibility() {
    def jdk17Modules = context.project.modules.findAll { JpsJavaExtensionService.instance.getLanguageLevel(it) == LanguageLevel.JDK_1_7 }
    if (jdk17Modules.isEmpty()) return

    context.messages.block("Checking compatibility with JDK 7") {
      downloadProguard()
      jdk17Modules.each {
        runProguard(it)
      }
    }
  }

  @SuppressWarnings("GrUnresolvedAccess")
  @CompileDynamic
  void runProguard(JpsModule module) {
    context.messages.progress("Checking compatibility with JDK 7 for '$module.name' module")
    def classpath = JpsJavaExtensionService.dependencies(module).withoutSdk().recursively().productionOnly().classes().withoutSelfModuleOutput().roots
    def moduleOutput = context.getModuleOutputPath(module)
    context.ant.proguard(target: "1.7", shrink: false, optimize: false, obfuscate: false, skipnonpubliclibraryclasses: false,
                         skipnonpubliclibraryclassmembers: false) {
      dontnote(filter: '**')

      //todo[nik]
      //workaround for groovy bug: InnerClasses attribute of org.jetbrains.plugins.gradle.tooling.builder.CopySpecWalker contains reference
      // to non-existing CopySpecWalker$Visitor$1 class
      dontwarn(filter: 'org.jetbrains.plugins.gradle.tooling.builder.CopySpecWalker')

      injar(path: moduleOutput)
      classpath.each {libraryjar(path: it.path)}
      libraryjar(path: "$jdk17Home/jre/lib/rt.jar")
    }
  }

  @SuppressWarnings("GrUnresolvedAccess")
  @CompileDynamic
  void downloadProguard() {
    context.messages.progress("Downloading proguard")
    context.ant.get(src: "$mavenCentralUrl/net/sf/proguard/proguard-base/$PROGUARD_VERSION/proguard-base-${PROGUARD_VERSION}.jar", dest: tempDir)
    context.ant.get(src: "$mavenCentralUrl/net/sf/proguard/proguard-anttask/$PROGUARD_VERSION/proguard-anttask-${PROGUARD_VERSION}.jar", dest: tempDir)
    context.ant.taskdef(resource: "proguard/ant/task.properties", classpath: "$tempDir/proguard-base-${PROGUARD_VERSION}.jar:$tempDir/proguard-anttask-${PROGUARD_VERSION}.jar")
  }
}
