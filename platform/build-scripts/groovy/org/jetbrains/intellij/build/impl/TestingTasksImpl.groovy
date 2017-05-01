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
import com.intellij.openapi.util.io.FileUtilRt
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.apache.tools.ant.AntClassLoader
import org.apache.tools.ant.types.Path
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.TestingOptions
import org.jetbrains.intellij.build.TestingTasks
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil

/**
 * @author nik
 */
@CompileStatic
class TestingTasksImpl extends TestingTasks {
  private final CompilationContext context
  private final TestingOptions options

  TestingTasksImpl(CompilationContext context, TestingOptions options) {
    this.options = options
    this.context = context
  }

  @Override
  void runTests(List<String> additionalJvmOptions, String defaultMainModule, String excludedSourceDirectory) {
    def compilationTasks = CompilationTasks.create(context)
    if (options.mainModule != null) {
      compilationTasks.compileModules(["tests_bootstrap"], [options.mainModule])
    }
    else {
      compilationTasks.compileAllModulesAndTests()
    }
    setupTestingDependencies()

    def mainModule = options.mainModule ?: defaultMainModule
    List<String> testsClasspath = context.projectBuilder.moduleRuntimeClasspath(context.findRequiredModule(mainModule), true)
    List<String> bootstrapClasspath = context.projectBuilder.moduleRuntimeClasspath(context.findRequiredModule("tests_bootstrap"), false)
    def classpathFile = new File("$context.paths.temp/junit.classpath")
    FileUtilRt.createParentDirs(classpathFile)
    classpathFile.text = testsClasspath.findAll({ new File(it).exists() }).join('\n')

    List<String> jvmArgs = [
      "-ea",
      "-server",
      "-Xbootclasspath/p:${context.projectBuilder.moduleOutput(context.findRequiredModule("boot"))}".toString(),
      "-XX:+HeapDumpOnOutOfMemoryError",
      "-XX:ReservedCodeCacheSize=300m",
      "-XX:SoftRefLRUPolicyMSPerMB=50",
      "-XX:+UseConcMarkSweepGC",
      "-XX:-OmitStackTraceInFastThrow"
    ]
    jvmArgs.addAll(additionalJvmOptions)
    if (options.jvmMemoryOptions != null) {
      jvmArgs.addAll(options.jvmMemoryOptions.split())
    }
    else {
      jvmArgs.addAll([
        "-Xmx750m",
        "-Xms750m",
        "-Dsun.io.useCanonCaches=false"
      ])
    }

    String tempDir = System.getProperty("teamcity.build.tempDir", System.getProperty("java.io.tmpdir"))

    Map<String, String> systemProperties = [
      "classpath.file"                         : classpathFile.absolutePath,
      "idea.platform.prefix"                   : options.platformPrefix,
      "idea.home.path"                         : context.paths.projectHome,
      "idea.config.path"                       : "$tempDir/config".toString(),
      "idea.system.path"                       : "$tempDir/system".toString(),
      "intellij.build.test.patterns"           : options.testPatterns,
      "intellij.build.test.groups"             : options.testGroups,
      "idea.performance.tests"                 : System.getProperty("idea.performance.tests"),
      "idea.coverage.enabled.build"            : System.getProperty("idea.coverage.enabled.build"),
      "bootstrap.testcases"                    : "com.intellij.AllTests",
      "java.io.tmpdir"                         : tempDir,
      "teamcity.build.tempDir"                 : tempDir,
      "teamcity.tests.recentlyFailedTests.file": System.getProperty("teamcity.tests.recentlyFailedTests.file"),
      "jna.nosys"                              : "true",
      "file.encoding"                          : "UTF-8",
      "io.netty.leakDetectionLevel"            : "PARANOID",
    ] as Map<String, String>

    (System.getProperties() as Hashtable<String, String>).each { String key, String value ->
      if (key.startsWith("pass.")) {
        systemProperties[key.substring("pass.".length())] = value
      }
    }

    if (excludedSourceDirectory != null) {
      List<JpsModule> excludedModules = context.project.modules.findAll {
        List<String> contentRoots = it.contentRootsList.urls
        !contentRoots.isEmpty() && FileUtil.isAncestor(new File(excludedSourceDirectory), JpsPathUtil.urlToFile(contentRoots.first()), false)
      }
      List<String> excludedRoots = excludedModules.collectMany {
        [context.projectBuilder.moduleOutput(it), context.projectBuilder.moduleTestsOutput(it)]
      }
      File excludedRootsFile = new File("$context.paths.temp/excluded.classpath")
      excludedRootsFile.text = excludedRoots.findAll { new File(it).exists() }.join('\n')
      systemProperties["exclude.tests.roots.file"] = excludedRootsFile.absolutePath
    }

    boolean suspendDebugProcess = options.suspendDebugProcess
    if (systemProperties["idea.performance.tests"] != "true") {
      String debuggerParameter = "-agentlib:jdwp=transport=dt_socket,server=y,suspend=${suspendDebugProcess ? "y" : "n"}"
      if (options.debugPort != -1) {
        debuggerParameter += ",address=$options.debugPort"
      }
      jvmArgs.add(debuggerParameter)
    }
    else {
      context.messages.info("Debugging disabled for performance tests")
      suspendDebugProcess = false
    }

    context.messages.info("Starting ${options.testGroups != null ? "test from groups '$options.testGroups'" : "all tests"}")
    context.messages.info("JVM options: $jvmArgs")
    context.messages.info("System properties: $systemProperties")
    context.messages.info("Bootstrap classpath: $bootstrapClasspath")
    context.messages.info("Tests classpath: $testsClasspath")

    if (suspendDebugProcess) {
      context.messages.info("""
------------->------------- The process suspended until remote debugger connects to debug port -------------<-------------
---------------------------------------^------^------^------^------^------^------^----------------------------------------
""")
    }
    runJUnitTask(jvmArgs, systemProperties, bootstrapClasspath)
  }

  @CompileDynamic
  private void runJUnitTask(List<String> jvmArgs, Map<String, String> systemProperties, List<String> bootstrapClasspath) {
    defineJunitTask(context.ant, "$context.paths.communityHome/lib")

    String junitTemp = "$context.paths.temp/junit"
    context.ant.mkdir(dir: junitTemp)

    context.ant.junit(fork: true, showoutput: true, logfailedtests: false, tempdir: junitTemp) {
      jvmArgs.each { jvmarg(value: it) }
      systemProperties.each { key, value ->
        if (value != null) {
          sysproperty(key: key, value: value)
        }
      }
      classpath {
        bootstrapClasspath.each {
          pathelement(location: it)
        }
      }

      test(name: 'com.intellij.tests.BootstrapTests')
    }
  }
  
  static boolean dependenciesInstalled
  private def setupTestingDependencies() {
    if (!dependenciesInstalled) {
      dependenciesInstalled = true
      context.gradle.run('Setting up testing dependencies', 'setupKotlinPlugin')
    }
  }

  static boolean taskDefined

  /**
   * JUnit is an optional dependency in Ant, so by defining its tasks dynamically we simplify setup for gant/Ant scripts, there is no need
   * to explicitly add its JARs to Ant libraries.
   */
  @CompileDynamic
  static private def defineJunitTask(AntBuilder ant, String communityLib) {
    if (taskDefined) return
    taskDefined = true
    def junitTaskLoaderRef = "JUNIT_TASK_CLASS_LOADER"
    Path pathJUnit = new Path(ant.project)
    pathJUnit.createPathElement().setLocation(new File("$communityLib/ant/lib/ant-junit.jar"))
    pathJUnit.createPathElement().setLocation(new File("$communityLib/ant/lib/ant-junit4.jar"))
    ant.project.addReference(junitTaskLoaderRef, new AntClassLoader(ant.project.getClass().getClassLoader(), ant.project, pathJUnit))
    ant.taskdef(name: "junit", classname: "org.apache.tools.ant.taskdefs.optional.junit.JUnitTask", loaderRef: junitTaskLoaderRef)
  }
}