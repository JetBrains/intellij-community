// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions

import com.intellij.execution.Location
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.UnknownConfigurationType
import com.intellij.execution.junit.RuntimeConfigurationProducer
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.FileContentUtilCore

@Suppress("DEPRECATION")
class RunCurrentFileInvalidPsiTest : BasePlatformTestCase() {
  fun testInvalidPsiFileGivesNoRunConfigurations() {
    RuntimeConfigurationProducer.RUNTIME_CONFIGURATION_PRODUCER.point.registerExtension(FileProducer(), testRootDisposable)
    val psiFile = myFixture.configureByText("README.txt", "text")
    runWriteAction { FileContentUtilCore.reparseFiles(psiFile.virtualFile) }
    assertFalse(psiFile.isValid)

    val errors = mutableListOf<Throwable?>()
    LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        errors.add(t)
        return Action.NONE
      }
    }) {
      assertEmpty(ExecutorAction.getRunConfigsForCurrentFile(psiFile, true))
    }
    assertEmpty(errors)
  }

  /** Stores the file as the source element, like `J2EEConfigurationProducer` does. */
  private class FileProducer : RuntimeConfigurationProducer(UnknownConfigurationType.getInstance() as ConfigurationType) {
    override fun getSourceElement(): PsiElement? = restoreSourceElement()

    override fun createConfigurationByElement(location: Location<*>, context: ConfigurationContext): RunnerAndConfigurationSettings? {
      storeSourceElement(location.psiElement.containingFile)
      return null
    }

    override fun compareTo(other: Any?): Int = PREFERED
  }
}
