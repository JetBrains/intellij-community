// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions.impl

import com.intellij.diff.chains.DiffRequestChain
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil.copyFrom
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.util.Consumer
import java.awt.event.MouseEvent

object GoToChangePopupBuilder {
  @JvmStatic
  fun create(chain: DiffRequestChain, onSelected: Consumer<in Int>, defaultSelection: Int): AnAction {
    if (chain is Chain) {
      val action = chain.createGoToChangeAction(onSelected, defaultSelection)
      if (action != null) return action
    }
    return SimpleGoToChangePopupAction(chain, onSelected, defaultSelection)
  }

  interface Chain : DiffRequestChain {
    fun createGoToChangeAction(onSelected: Consumer<in Int>, defaultSelection: Int): AnAction?
  }

  abstract class BaseGoToChangePopupAction : ActionGroup(), DumbAware {
    init {
      copyFrom(this, "GotoChangedFile")
      isPopup = true
      getTemplatePresentation().isPerformGroup = true
    }

    final override fun getChildren(e: AnActionEvent?): Array<AnAction> = EMPTY_ARRAY

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = canNavigate() && e.getData(DiffDataKeys.DIFF_CONTEXT) != null
    }

    protected abstract fun canNavigate(): Boolean

    final override fun actionPerformed(e: AnActionEvent) {
      val popup = createPopup(e)

      val event = e.inputEvent
      if (event is MouseEvent) {
        popup.showUnderneathOf(event.component)
      }
      else {
        popup.showInBestPositionFor(e.dataContext)
      }
    }

    protected abstract fun createPopup(e: AnActionEvent): JBPopup
  }

  private class SimpleGoToChangePopupAction(
    private val chain: DiffRequestChain,
    private val onSelected: Consumer<in Int>,
    private val defaultSelection: Int,
  ) : BaseGoToChangePopupAction() {
    override fun canNavigate(): Boolean = chain.requests.size > 1

    override fun createPopup(e: AnActionEvent): JBPopup = JBPopupFactory.getInstance().createListPopup(MyListPopupStep())

    private inner class MyListPopupStep :
      BaseListPopupStep<DiffRequestProducer>(
        DiffBundle.message("action.presentation.go.to.change.text"),
        chain.getRequests()
      ) {
      init {
        defaultOptionIndex = defaultSelection
      }

      override fun getTextFor(value: DiffRequestProducer): String = value.getName()

      override fun isSpeedSearchEnabled(): Boolean = true

      override fun onChosen(selectedValue: DiffRequestProducer?, finalChoice: Boolean): PopupStep<*>? {
        return doFinalStep {
          val index = chain.getRequests().indexOf(selectedValue)
          onSelected.consume(index)
        }
      }
    }
  }
}
