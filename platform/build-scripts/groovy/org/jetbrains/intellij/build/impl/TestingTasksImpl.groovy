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

import java.util.function.Predicate
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
  void runTests(List<String> additionalJvmOptions, String defaultMainModule, Predicate<File> rootExcludeCondition) {
    def compilationTasks = CompilationTasks.create(context)
    if (options.mainModule != null) {
      compilationTasks.compileModules(["tests_bootstrap"], [options.mainModule, "platform-build-scripts"])
    }
    else {
      compilationTasks.compileAllModulesAndTests()
    }
    setupTestingDependencies()

    def mainModule = options.mainModule ?: defaultMainModule
    List<String> testsClasspath = context.getModuleRuntimeClasspath(context.findRequiredModule(mainModule), true)
    List<String> bootstrapClasspath = context.getModuleRuntimeClasspath(context.findRequiredModule("tests_bootstrap"), false)
    def classpathFile = new File("$context.paths.temp/junit.classpath")
    FileUtilRt.createParentDirs(classpathFile)
    classpathFile.text = testsClasspath.findAll({ new File(it).exists() }).join('\n')

    List<String> jvmArgs = [
      "-ea",
      "-server",
      "-Xbootclasspath/a:${context.getModuleOutputPath(context.findRequiredModule("boot"))}".toString(),
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
      "teamcity.buildConfName"                 : System.getProperty("teamcity.buildConfName"),
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

    if (rootExcludeCondition != null) {
      List<JpsModule> excludedModules = context.project.modules.findAll {
        List<String> contentRoots = it.contentRootsList.urls
        !contentRoots.isEmpty() && rootExcludeCondition.test(JpsPathUtil.urlToFile(contentRoots.first()))
      }
      List<String> excludedRoots = excludedModules.collectMany {
        [context.getModuleOutputPath(it), context.getModuleTestsOutputPath(it)]
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
    if (options.customJrePath != null) {
      context.messages.info("JVM: $options.customJrePath")
    }
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
    if (isBootstrapSuiteDefault())
      runJUnitTask(jvmArgs, systemProperties, bootstrapClasspath)
     else
      //run other suites instead of BootstrapTests
      runJUnitTask(jvmArgs, systemProperties, testsClasspath)
  }

  @CompileDynamic
  private void runJUnitTask(List<String> jvmArgs, Map<String, String> systemProperties, List<String> bootstrapClasspath) {
    defineJunitTask(context.ant, "$context.paths.communityHome/lib")

    String junitTemp = "$context.paths.temp/junit"
    context.ant.mkdir(dir: junitTemp)

    List<String> teamCityFormatterClasspath = createTeamCityFormatterClasspath()

    String jvmExecutablePath = options.customJrePath != null ? "$options.customJrePath/bin/java" : ""
    context.ant.junit(fork: true, showoutput: true, logfailedtests: false, tempdir: junitTemp, jvm: jvmExecutablePath, printsummary: (underTeamCity ? "off" : "on")) {
      jvmArgs.each { jvmarg(value: it) }
      systemProperties.each { key, value ->
        if (value != null) {
          sysproperty(key: key, value: value)
        }
      }

      if (teamCityFormatterClasspath != null) {
        classpath {
          teamCityFormatterClasspath.each {
            pathelement(location: it)
          }
        }
        formatter(classname: "jetbrains.buildServer.ant.junit.AntJUnitFormatter2", usefile: false)
        context.messages.info("Added TeamCity's formatter to JUnit task")
      }
      if (!underTeamCity) {
        classpath {
          pathelement(location: context.getModuleTestsOutputPath(context.findRequiredModule("platform-build-scripts")))
        }
        formatter(classname: "org.jetbrains.intellij.build.JUnitLiveTestProgressFormatter", usefile: false)
      }

      classpath {
        bootstrapClasspath.each {
          pathelement(location: it)
        }
      }

      test(name: options.bootstrapSuite)
    }
  }

  /**
   * In simple cases when JUnit tests are started from Ant scripts TeamCity will automatically add its formatter to JUnit task. However it
   * doesn't work if JUnit task is called from Groovy code via a new instance of AntBuilder, so in such cases we need to add the formatter
   * explicitly.
   * @return classpath for TeamCity's JUnit formatter or {@code null} if the formatter shouldn't be added
   */
  private List<String> createTeamCityFormatterClasspath() {
    if (!underTeamCity) return null

    if (context.ant.project.buildListeners.any { it.class.name.startsWith("jetbrains.buildServer.") }) {
      context.messages.info("TeamCity's BuildListener is registered in the Ant project so its formatter will be added to JUnit task automatically.")
      return null
    }

    String agentHomeDir = System.getProperty("agent.home.dir")
    if (agentHomeDir == null) {
      context.messages.error("'agent.home.dir' system property isn't set, cannot add TeamCity JARs to classpath.")
    }
    List<String> classpath = [
      "$agentHomeDir/lib/runtime-util.jar",
      "$agentHomeDir/lib/serviceMessages.jar",
      "$agentHomeDir/plugins/antPlugin/ant-runtime.jar",
      "$agentHomeDir/plugins/junitPlugin/junit-runtime.jar",
      "$agentHomeDir/plugins/junitPlugin/junit-support.jar"
    ].collect {it.toString()}
    classpath.each {
      if (!new File(it).exists()) {
        context.messages.error("Cannot add required JARs from $agentHomeDir to classpath: $it doesn't exist")
      }
    }
    return classpath
  }

  private static boolean isUnderTeamCity() {
    System.getProperty("teamcity.buildType.id") != null
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

  private boolean isBootstrapSuiteDefault() {
    return options.bootstrapSuite == TestingOptions.BOOTSTRAP_SUITE_DEFAULT
  }
}