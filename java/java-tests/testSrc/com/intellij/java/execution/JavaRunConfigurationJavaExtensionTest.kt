// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution

import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.testFramework.PlatformTestUtil

class JavaRunConfigurationJavaExtensionTest : RunConfigurationJavaExtensionManagerTestCase() {
  override fun getTestAppPath(): String = "${PlatformTestUtil.getCommunityPath()}/java/java-tests/testData/tinyApp/"

  fun `test only applicable configuration extensions should be processed`() {
    doTestOnlyApplicableConfigurationExtensionsShouldBeProcessed(createJavaApplicationRunConfiguration(), "Hello World!")
  }

  private fun createJavaApplicationRunConfiguration() =
    ApplicationConfiguration("HelloWorldApplicationConfiguration", project).apply {
      setModule(module)
      mainClassName = "HelloWorld"
    }
}
