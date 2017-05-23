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
package com.intellij.java.execution

import com.intellij.execution.configurations.SimpleJavaParameters
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CommandLineWrapperRunTest : BareTestFixtureTestCase() {
  private lateinit var sdk: Sdk

  @Before fun setUp() {
    sdk = JavaSdk.getInstance().createJdk("test jdk", System.getProperty("java.home"), true)
  }

  @Test fun `no dynamic classpath`() = doTest()
  @Test fun `dynamic classpath, class loader`() = doTest(true, false)
  @Test fun `dynamic classpath, classpath JAR`() = doTest(true, true)
  @Test fun `dynamic parameters, class loader`() = doTest(true, false, true)
  @Test fun `dynamic parameters, classpath JAR`() = doTest(true, true, true)

  @Suppress("UsePropertyAccessSyntax")
  private fun doTest(dynamicCp: Boolean = false, classPathJar: Boolean = false, dynamicArgs: Boolean = false) {
    val parameters = SimpleJavaParameters()
    parameters.jdk = sdk
    parameters.vmParametersList.addProperty(Helper.PROPERTY1)
    parameters.vmParametersList.addProperty(Helper.PROPERTY2, "a value")
    parameters.classPath.add(PathManager.getJarPathForClass(Helper::class.java))
    parameters.classPath.add(PathManager.getJarPathForClass(Unit::class.java))
    parameters.mainClass = Helper::class.java.name
    parameters.programParametersList.add("first parameter")
    parameters.programParametersList.add("next parameter")

    parameters.setUseDynamicClasspath(dynamicCp)
    parameters.setUseClasspathJar(classPathJar)
    parameters.setUseDynamicVMOptions(dynamicArgs)
    parameters.setUseDynamicParameters(dynamicArgs)

    val command = parameters.toCommandLine()
    val out = ExecUtil.execAndGetOutput(command)
    assertEquals(out.stderr, 0, out.exitCode)
    assertEquals("${Helper.PROPERTY1}=\n" +
                 "${Helper.PROPERTY2}=a value\n" +
                 "arg[0]=first parameter\n" +
                 "arg[1]=next parameter\n", out.stdout)
  }
}

internal object Helper {
  val PROPERTY1 = "idea.wrapper.exec.test.1"
  val PROPERTY2 = "idea.wrapper.exec.test.2"

  @JvmStatic
  fun main(args: Array<String>) {
    println(PROPERTY1 + "=" + System.getProperty(PROPERTY1))
    println(PROPERTY2 + "=" + System.getProperty(PROPERTY2))
    for (i in args.indices) {
      println("arg[" + i + "]=" + args[i])
    }
  }
}