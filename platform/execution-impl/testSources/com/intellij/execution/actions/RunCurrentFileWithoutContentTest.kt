// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.UnknownConfigurationType
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.psi.PsiElement
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RunCurrentFileWithoutContentTest : BasePlatformTestCase() {
  private val producer = CountingProducer()

  override fun setUp() {
    super.setUp()
    Registry.get("run.current.file.item.in.run.configurations.combobox").setValue(true, testRootDisposable)
    RunConfigurationProducer.EP_NAME.point.registerExtension(producer, testRootDisposable)
  }

  fun testFileWithoutContentIsNotRunnableAndAsksNoProducer() {
    myFixture.openFileInEditor(object : LightVirtualFile("tab", "text"), VirtualFileWithoutContent {})

    assertNotRunnable(update())
    assertEquals(0, producer.askCount)
  }

  fun testPsiOfFileWithoutContentGivesNoRunConfigurations() {
    val file = object : LightVirtualFile("tab", "text"), VirtualFileWithoutContent {}
    val psiFile = runReadAction { psiManager.findFile(file) }!!

    assertEmpty(runReadAction { ExecutorAction.getRunConfigsForCurrentFile(psiFile, true) })
    assertEquals(0, producer.askCount)
  }

  fun testFileWithContentAsksProducer() {
    myFixture.configureByText("README.txt", "text")

    assertNotRunnable(update())
    assertEquals(1, producer.askCount)
  }

  private fun update(): Presentation {
    val action = object : ExecutorAction(DefaultRunExecutor.getRunExecutorInstance()) {}
    val event = TestActionEvent.createTestEvent(action, SimpleDataContext.getProjectContext(project))
    runReadAction { action.update(event) }
    return event.presentation
  }

  private fun assertNotRunnable(presentation: Presentation) {
    assertFalse(presentation.isEnabled)
    assertEquals(ExecutionBundle.message("run.button.on.toolbar.tooltip.current.file.not.runnable"), presentation.text)
    assertSame(DefaultRunExecutor.getRunExecutorInstance().icon, presentation.icon)
  }

  private class CountingProducer : LazyRunConfigurationProducer<RunConfiguration>() {
    var askCount: Int = 0

    override fun getConfigurationFactory(): ConfigurationFactory = UnknownConfigurationType.getInstance().configurationFactories.single()

    override fun setupConfigurationFromContext(
      configuration: RunConfiguration,
      context: ConfigurationContext,
      sourceElement: Ref<PsiElement>,
    ): Boolean {
      askCount++
      return false
    }

    override fun isConfigurationFromContext(configuration: RunConfiguration, context: ConfigurationContext): Boolean = false
  }
}
