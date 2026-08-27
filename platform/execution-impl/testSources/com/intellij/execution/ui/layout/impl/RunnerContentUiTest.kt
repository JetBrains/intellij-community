// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui.layout.impl

import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.rules.ProjectModelRule
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import javax.swing.JPanel

class RunnerContentUiTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val projectModel = ProjectModelRule()

  @Test
  fun `restore layout does not duplicate tabs in another session`(): Unit =
    timeoutRunBlocking(context = Dispatchers.EDT) {
      val runnerId = "RunnerContentUiTest"
      val disposable = projectModel.disposableRule.disposable
      val firstUi = createUi(runnerId, "first", disposable)
      val secondUi = createUi(runnerId, "second", disposable)

      assertThat(firstUi.contentUI.tabs.tabCount).isEqualTo(2)
      assertThat(secondUi.contentUI.tabs.tabCount).isEqualTo(2)

      secondUi.contentUI.restoreLayout()
      firstUi.contentUI.restoreLayout()

      assertThat(firstUi.contentUI.tabs.tabCount).isEqualTo(2)
      assertThat(secondUi.contentUI.tabs.tabCount).isEqualTo(2)
    }

  private fun createUi(runnerId: String, sessionName: String, disposable: Disposable): RunnerLayoutUiImpl {
    val ui = RunnerLayoutUiImpl(projectModel.project, disposable, runnerId, "Debug", sessionName)
    ui.initTabDefaults(0, "First", null)
    ui.initTabDefaults(1, "Second", null)
    ui.addContent(ui.createContent("first", JPanel(), "First", null, null), 0, PlaceInGrid.center, false)
    ui.addContent(ui.createContent("second", JPanel(), "Second", null, null), 1, PlaceInGrid.center, false)
    return ui
  }
}
