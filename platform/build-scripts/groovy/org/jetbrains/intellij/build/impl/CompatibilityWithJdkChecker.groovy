/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.java.LanguageLevel
import org.jetbrains.jps.model.module.JpsModule

import java.util.function.Supplier
/**
 * We currently use JDK 1.6 and JDK 1.8 to compile modules in IntelliJ project. So if some module has Java 7 language level, it'll be compiled
 * using JDK 1.8 and this class is used to check that classes from such modules actually don't use API which isn't present in JDK 1.7. Also
 * some modules which has Java 6 language level are need to be compiled by JDK 1.8 (e.g. because they have compile-only dependencies
 * on libraries which use newer Java version) and this class is used to check compatibility of their classes with JDK 1.6 as well.
 */
@CompileStatic
class CompatibilityWithJdkChecker {
  private static final String PROGUARD_VERSION = "5.3.3"
  private final CompilationContext context
  private final String tempDir
  private final String mavenCentralUrl

  CompatibilityWithJdkChecker(CompilationContext context) {
    this.context = context
    tempDir = "$context.paths.temp/compatibility-with-jdk-check"
    mavenCentralUrl = System.getProperty("intellij.build.maven.central.url", "http://repo1.maven.org/maven2")
    FileUtil.createDirectory(new File(tempDir))
  }

  void checkCompatibility(LanguageLevel targetJavaVersion, Supplier<String> jdkHomeEvaluator) {
    def modulesToCheck = context.project.modules.findAll {
      JpsJavaExtensionService.instance.getLanguageLevel(it) == targetJavaVersion && it.sdkReferencesTable.getSdkReference(JpsJavaSdkType.INSTANCE) == null
    }
    if (modulesToCheck.isEmpty()) return

    def jdkHome = jdkHomeEvaluator.get()
    if (!new File(jdkHome).exists()) {
      context.messages.error("Failed to check compatibility with JDK $targetJavaVersion: $jdkHome does not exist")
    }

    context.messages.block("Checking compatibility with JDK $targetJavaVersion.complianceOption") {
      downloadProguard()
      modulesToCheck.each {
        runProguard(it, jdkHome, targetJavaVersion)
      }
    }
  }

  @SuppressWarnings("GrUnresolvedAccess")
  @CompileDynamic
  void runProguard(JpsModule module, String jdkHome, LanguageLevel targetJavaVersion) {
    context.messages.progress("Checking compatibility with JDK $targetJavaVersion.complianceOption for '$module.name' module")
    def classpath = JpsJavaExtensionService.dependencies(module).withoutSdk().recursively().productionOnly().classes().withoutSelfModuleOutput().roots
    def moduleOutput = context.getModuleOutputPath(module)
    context.ant.proguard(target: targetJavaVersion.complianceOption, shrink: false, optimize: false, obfuscate: false, skipnonpubliclibraryclasses: false,
                         skipnonpubliclibraryclassmembers: false) {
      dontnote(filter: '**')

      //todo[nik]
      //workaround for groovy bug: InnerClasses attribute of org.jetbrains.plugins.gradle.tooling.builder.CopySpecWalker contains reference
      // to non-existing CopySpecWalker$Visitor$1 class
      dontwarn(filter: 'org.jetbrains.plugins.gradle.tooling.builder.CopySpecWalker')

      injar(path: moduleOutput)
      classpath.each {libraryjar(path: it.path)}
      libraryjar(path: "$jdkHome/jre/lib/rt.jar")
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

  static void run(CompilationContext context) {
    def checker = new CompatibilityWithJdkChecker(context)
    checker.checkCompatibility(LanguageLevel.JDK_1_6, {
      def sdk = context.projectModel.global.libraryCollection.findLibrary("IDEA jdk")
      if (sdk == null) {
        context.messages.error("Failed to check compatibility with JDK 6: 'IDEA jdk' is not defined")
      }
      sdk.asTyped(JpsJavaSdkType.INSTANCE).properties.homePath
    })
    checker.checkCompatibility(LanguageLevel.JDK_1_7, {
      def jdk7Home = System.getenv("JDK_17_x64")
      if (jdk7Home == null) {
        context.messages.error("Failed to check compatibility with JDK 7: JDK_17_x64 is not specified")
      }
      jdk7Home
    })
  }
}
