// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.find.impl.TextSearchContributor
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil.waitForFuture
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usages.UsageInfo2UsageAdapter
import junit.framework.TestCase
import java.awt.Font.BOLD

class TextSearchContributorTest : BasePlatformTestCase() {
  fun testPresentation() {
    UsageInfo2UsageAdapter.disableAutomaticPresentationCalculationInTests {
      myFixture.addFileToProject("a.txt", """
      14516b6c-f7e7-444d-b011-3fcbdc71f7bd
      ffc9fc9e-b527-44f6-85ea-eedafea0f30d
      c8a1f836-5198-41b5-a906-793bd9d0d241
    """.trimIndent())

      val contributor = TextSearchContributor(createEvent(project))
      val ui = SearchEverywhereUI(project, listOf(contributor))
      ui.switchToTab(contributor.searchProviderId)
      val elements = waitForFuture(ui.findElementsForPattern("eedafea0f30d"))
      val element = elements.single() as SearchEverywhereItem
      val pattern = element.presentation.text.single { it.text == "eedafea0f30d" }
      TestCase.assertEquals(BOLD, pattern.attributes.fontType)
    }
  }

  private fun createEvent(project: Project): AnActionEvent {
    val projectContext = SimpleDataContext.getProjectContext(project)
    return AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, projectContext)
  }
}