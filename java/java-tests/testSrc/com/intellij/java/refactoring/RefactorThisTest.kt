// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.refactoring.actions.PullUpAction
import com.intellij.refactoring.actions.RefactoringQuickListPopupAction
import com.intellij.testFramework.LightJavaCodeInsightTestCase
import org.jetbrains.annotations.NonNls

class RefactorThisTest: LightJavaCodeInsightTestCase() {
  private val BASE_PATH: @NonNls String = "/refactoring/refactorThis"

  fun testPullMembersUp() {
    assertTrue(doActionExists<PullUpAction>())
  }

  fun testPullMembersUp1() {
    assertFalse(doActionExists<PullUpAction>())
  }

  private inline fun <reified A> doActionExists(): Boolean {
    configureByFile("$BASE_PATH/${getTestName(false)}.java")
    return findAvailableActions().any { action -> action is A }
  }

  private fun findAvailableActions(): List<AnAction> {
    val action = RefactoringQuickListPopupAction()
    val group = DefaultActionGroup()
    action.fillActions(project, group, currentEditorDataContext)
    return group.childActionsOrStubs.toList()
  }

}