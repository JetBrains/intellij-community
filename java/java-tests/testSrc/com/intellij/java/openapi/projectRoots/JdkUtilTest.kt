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
package com.intellij.java.openapi.projectRoots

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.projectRoots.JdkUtil
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.rt.execution.CommandLineWrapper
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.util.SystemProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

class JdkUtilTest : BareTestFixtureTestCase() {
  companion object {
    private val jdk = lazy { SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()) }
    private val wrapper = CommandLineWrapper::class.java.name
  }

  private val parameters = SimpleJavaParameters()
  private var filesToDelete: Collection<File>? = null

  @Before fun setUp() {
    parameters.jdk = jdk.value
    parameters.classPath.add("/classes/hello.jar")
    parameters.vmParametersList.add("-Xmx256m")
    parameters.vmParametersList.add("-Dan.option=1")
    parameters.mainClass = "hello.Main"
    parameters.programParametersList.add("hello")
    parameters.setUseDynamicClasspath(true)
  }

  @After fun tearDown() {
    filesToDelete?.forEach { FileUtil.delete(it) }
  }

  @Test fun noDynamicClasspath() {
    parameters.setUseDynamicClasspath(false)
    doTest("-Xmx256m", "-Dan.option=1", "-classpath", "/classes/hello.jar", "hello.Main", "hello")
  }

  @Test fun noDynamicClasspathInModuleMode() {
    parameters.setUseDynamicClasspath(false)
    setModuleMode()
    doTest("-Xmx256m", "-Dan.option=1", "-p", "/classes/hello.jar", "-m", "hello/hello.Main", "hello")
  }

  @Test fun noDynamicClasspathInJarMode() {
    parameters.mainClass = null
    parameters.jarPath = "/classes/main.jar"
    doTest("-Xmx256m", "-Dan.option=1", "-classpath", "/classes/hello.jar", "-jar", "/classes/main.jar", "hello")
  }

  @Test fun dynamicClasspathWithJar() {
    parameters.isUseClasspathJar = true
    doTest("-Xmx256m", "-Dan.option=1", "-classpath", "#classpath.jar#", "hello.Main", "hello")
  }

  @Test fun dynamicClasspathWithJarAndParameters() {
    parameters.isUseClasspathJar = true
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    doTest("-Xmx256m", "-classpath", "#idea_rt#:#classpath.jar#", wrapper, "#classpath.jar#", "hello.Main")
  }

  @Test fun dynamicClasspathWithWrapper() {
    parameters.isUseClasspathJar = false
    doTest("-Xmx256m", "-Dan.option=1", "-classpath", "#idea_rt#", wrapper, "#classpath#", "hello.Main", "hello")
  }

  @Test fun dynamicClasspathWithWrapperAndParameters() {
    parameters.isUseClasspathJar = false
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    doTest("-Xmx256m", "-classpath", "#idea_rt#", wrapper, "#classpath#", "@vm_params", "#vm_params#", "@app_params", "#app_params#", "hello.Main")
  }

  @Test fun dynamicClasspathWithArgFile() {
    setModuleMode()
    doTest("-Xmx256m", "-Dan.option=1", "#arg_file#", "-m", "hello/hello.Main", "hello")
  }

  @Test fun dynamicClasspathWithArgFileAndParameters() {
    setModuleMode()
    parameters.setUseDynamicVMOptions(true)
    parameters.setUseDynamicParameters(true)
    doTest("#arg_file#")
  }

  private fun doTest(vararg expected: String) {
    val cmd = JdkUtil.setupJVMCommandLine(parameters)
    filesToDelete = cmd.getUserData(OSProcessHandler.DELETE_FILES_ON_TERMINATION)

    val actual = cmd.getCommandLineList("-")
    val toCompare = mutableListOf<String>()
    actual.forEachIndexed { i, arg ->
      if (i > 0 && !arg.startsWith("-Dfile.encoding=")) {
        toCompare += when {
          arg.contains(File.pathSeparatorChar) -> arg.splitToSequence(File.pathSeparatorChar).map { mapPath(it) }.joinToString(":")
          arg.contains(File.separatorChar) -> mapPath(arg)
          else -> arg
        }
      }
    }

    assertThat(toCompare).containsExactly(*expected)
  }

  private fun mapPath(p: String): String {
    val path = p.replace(File.separatorChar, '/')
    val tempDir = FileUtil.getTempDirectory().replace(File.separatorChar, '/')
    return when {
      path.matches("$tempDir/classpath\\d*.jar".toRegex()) -> "#classpath.jar#"
      path.matches("$tempDir/idea_classpath\\d*".toRegex()) -> "#classpath#"
      path.matches("$tempDir/idea_vm_params\\d*".toRegex()) -> "#vm_params#"
      path.matches("$tempDir/idea_app_params\\d*".toRegex()) -> "#app_params#"
      path.matches("@$tempDir/idea_arg_file\\d*".toRegex()) -> "#arg_file#"
      path.endsWith("/java-runtime") || path.endsWith("/idea_rt.jar") -> "#idea_rt#"
      else -> path
    }
  }

  private fun setModuleMode() {
    parameters.modulePath.addAll(parameters.classPath.pathList)
    parameters.classPath.clear()
    parameters.moduleName = "hello"
  }
}