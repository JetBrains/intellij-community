// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.execution

import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaRunConfigurationJavaExtensionTest {
  private val execution by runConfigurationExtensionTestFixture()

  @Test
  fun `test only applicable configuration extensions should be processed`() {
    doTestOnlyApplicableConfigurationExtensionsShouldBeProcessed(execution, createJavaApplicationRunConfiguration(), "Hello World!")
  }

  private fun createJavaApplicationRunConfiguration() =
    ApplicationConfiguration("HelloWorldApplicationConfiguration", execution.project).apply {
      setModule(execution.module)
      mainClassName = "HelloWorld"
    }
}
