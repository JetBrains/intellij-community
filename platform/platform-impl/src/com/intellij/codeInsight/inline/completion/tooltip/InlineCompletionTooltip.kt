// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.tooltip.onboarding.InlineCompletionOnboardingComponent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.client.currentSession
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.LightweightHint
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.preferredHeight
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object InlineCompletionTooltip {
  private val tooltipKey = Key<Unit>("EDITOR_HAS_INLINE_TOOLTIP")

  @RequiresEdt
  fun show(session: InlineCompletionSession) {
    val editor = session.context.editor
    if (tooltipKey.isIn(editor)) {
      return
    }

    if (!application.currentSession.isLocal && !System.getProperty("inline.completion.tooltip.remote.dev").toBoolean()) {
      return
    }

    val activeLookup = LookupManager.getActiveLookup(editor)
    if (activeLookup?.isPositionedAboveCaret == true) {
      return
    }

    val panel = InlineCompletionTooltipComponent().create(session)

    val hint = object : LightweightHint(panel) {
      private val hintShownMs = System.currentTimeMillis()
      private var hintTimeRegistered = false

      override fun onPopupCancel() {
        // on hint hide
        editor.putUserData(tooltipKey, null)

        // This method might be called several times
        if (!hintTimeRegistered) {
          val hintHiddenMs = System.currentTimeMillis()
          InlineCompletionOnboardingComponent.getInstance().fireTooltipLivedFor(hintHiddenMs - hintShownMs)
          hintTimeRegistered = true
        }
      }
    }.apply {
      setForceShowAsPopup(true)
      setBelongsToGlobalPopupStack(false)
    }

    val offset = runReadAction { editor.caretModel.offset }
    val location = HintManagerImpl.getHintPosition(
      hint, editor,
      editor.offsetToLogicalPosition(offset),
      HintManager.ABOVE
    ).apply { y -= (panel.preferredHeight + JBUIScale.scale(8)) }

    HintManagerImpl.getInstanceImpl().showEditorHint(
      hint, editor, location,
      HintManager.HIDE_BY_CARET_MOVE or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.HIDE_BY_SCROLLING,
      0, false,
      HintManagerImpl.createHintHint(editor, location, hint, HintManager.ABOVE).setContentActive(false)
    )
    editor.putUserData(tooltipKey, Unit)

    Disposer.register(session) {
      hint.hide()
    }
  }
}
