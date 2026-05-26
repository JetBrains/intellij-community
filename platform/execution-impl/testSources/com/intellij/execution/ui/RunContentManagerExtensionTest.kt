// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.openapi.project.Project
import com.intellij.ui.content.ContentManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class RunContentManagerExtensionTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val projectModel = ProjectModelRule()

  @Test
  fun `optional extension can be absent`() {
    val project = projectModel.project
    ExtensionTestUtil.maskExtensions(RunContentManagerExtension.EP_NAME, emptyList(), projectModel.disposableRule.disposable)

    assertThat(RunContentManagerExtension.getInstance(project)).isNull()
    assertThat(RunContentManagerExtension.getConfiguredRunConfigurationTypesIfAvailable(project)).isEmpty()
  }

  @Test
  fun `non-creating hooks do not fall back to creating hooks`() {
    val project = projectModel.project
    val extension = TestRunContentManagerExtension()
    ExtensionTestUtil.maskExtensions(RunContentManagerExtension.EP_NAME, listOf(extension), projectModel.disposableRule.disposable)

    assertThat(RunContentManagerExtension.getInstance(project)?.getToolWindowIdIfCreated(project)).isNull()
    assertThat(RunContentManagerExtension.getInstance(project)?.getContentManagerIfCreated(project)).isNull()
    assertThat(extension.toolWindowIdRequests).isZero()
    assertThat(extension.contentManagerRequests).isZero()
  }

  private class TestRunContentManagerExtension : RunContentManagerExtension {
    var toolWindowIdRequests = 0
    var contentManagerRequests = 0

    override fun getToolWindowId(project: Project): String {
      toolWindowIdRequests++
      return "Services"
    }

    override fun getContentManager(project: Project): ContentManager? {
      contentManagerRequests++
      return null
    }
  }
}
