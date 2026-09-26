// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.rider

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionWrapper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class RiderMainToolbarRootPaneManagerTest {
  @Test
  fun filteredToolbarActionsExcludeWrappedDelegateActionIds() {
    val actionManager = ActionManager.getInstance()
    val backAction = checkNotNull(actionManager.getAction(IdeActions.ACTION_GOTO_BACK))
    val forwardAction = checkNotNull(actionManager.getAction(IdeActions.ACTION_GOTO_FORWARD))
    val wrappedForwardAction = object : AnActionWrapper(forwardAction) {}
    val toolbarGroup = DefaultActionGroup().apply {
      add(backAction)
      add(wrappedForwardAction)
    }

    val filtered = filteredToolbarActions(
      toolbarGroup = toolbarGroup,
      event = AnActionEvent.createEvent(DataContext.EMPTY_CONTEXT, Presentation(), "test", ActionUiKind.NONE, null),
      excludedActionIds = setOf(IdeActions.ACTION_GOTO_FORWARD),
      actionManager = actionManager,
    )

    assertThat(filtered).containsExactly(backAction)
  }

  @Suppress("UNCHECKED_CAST")
  private fun filteredToolbarActions(
    toolbarGroup: ActionGroup,
    event: AnActionEvent,
    excludedActionIds: Set<String>,
    actionManager: ActionManager,
  ): Array<com.intellij.openapi.actionSystem.AnAction> {
    val method = Class.forName("com.intellij.ide.navigationToolbar.rider.RiderMainToolbarRootPaneExtensionKt")
      .declaredMethods
      .single { it.name.startsWith("filteredToolbarActions") && it.parameterCount == 4 }
    method.isAccessible = true
    return method.invoke(null, toolbarGroup, event, excludedActionIds, actionManager) as Array<com.intellij.openapi.actionSystem.AnAction>
  }
}
