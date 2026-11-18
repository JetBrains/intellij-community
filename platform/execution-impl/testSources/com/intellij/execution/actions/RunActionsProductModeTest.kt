// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.platform.productMode.ProductMode
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The Run and Debug actions have no product mode check of their own: a light mode that loads them shows them.
 * A light mode without the execution switch does not load `intellij.platform.execution.impl` at all.
 */
@TestApplication
class RunActionsProductModeTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `run and debug actions are visible in every product mode`() {
    for (mode in listOf(ProductMode.MONOLITH, ProductMode.LIGHT_MONOLITH, ProductMode.LIGHT_REMOTE)) {
      assertActionsVisible(mode)
    }
  }

  private fun assertActionsVisible(mode: ProductMode) {
    val modeDisposable = Disposer.newDisposable(disposable)
    application.replaceService(IdeProductMode::class.java, object : IdeProductMode {
      override val currentMode: ProductMode = mode
    }, modeDisposable)

    val actionIds = listOf("RunMenu", "RunConfiguration", "RedesignedRunConfigurationSelector",
                           IdeActions.ACTION_DEFAULT_RUNNER, IdeActions.ACTION_DEFAULT_DEBUGGER)
    for (actionId in actionIds) {
      val action = ActionManager.getInstance().getAction(actionId)
      assertThat(action).describedAs(actionId).isNotNull()
      val event = TestActionEvent.createTestEvent(action, DataContext.EMPTY_CONTEXT)
      ActionUtil.updateAction(action, event)
      assertThat(event.presentation.isVisible).describedAs("$actionId visible in '${mode.id}'").isTrue()
    }
    Disposer.dispose(modeDisposable)
  }
}
