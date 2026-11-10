// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.intention.AbstractIntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The light bulb sorts the intentions on the EDT.
 * The sort must use the priority that [IntentionActionWithTextCaching] got when it was created.
 */
@TestApplication
internal class IntentionsOrderPriorityTest {
  companion object {
    private val projectFixture = projectFixture()
    private val psiFileFixture = projectFixture.moduleFixture("src").sourceRootFixture().psiFileFixture("dummy.txt", "text")
  }

  @Test
  fun `sort does not ask the action for the priority`() {
    val action = CountingAction("Action", PriorityAction.Priority.HIGH)
    val intention = IntentionActionWithTextCaching(action)
    action.priorityCount = 0

    val context = CachedIntentions(projectFixture.get(), psiFileFixture.get(), null)
    DefaultIntentionsOrderProvider().getSortedIntentions(context, listOf(intention))
    assertEquals(0, action.priorityCount)
    assertEquals(DefaultIntentionsOrderProvider.getPriorityWeight(PriorityAction.Priority.HIGH),
                 DefaultIntentionsOrderProvider.getPriorityWeight(intention))
  }

  private class CountingAction(private val name: String, private val priority: PriorityAction.Priority)
    : AbstractIntentionAction(), PriorityAction {
    var priorityCount = 0

    override fun getText(): String = name

    override fun invoke(project: Project, editor: Editor?, psiFile: PsiFile?) {}

    override fun getPriority(): PriorityAction.Priority {
      priorityCount++
      return priority
    }
  }
}
