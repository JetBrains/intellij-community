// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.tooltip

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.inline.completion.session.InlineCompletionSession
import com.intellij.codeInsight.inline.completion.tooltip.onboarding.InlineCompletionOnboardingComponent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.client.currentSession
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.removeUserData
import com.intellij.ui.LightweightHint
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.preferredHeight
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
object InlineCompletionTooltip {

  private val tooltipHintKey = Key<LightweightHint>("intellij.platform.inline.completion.tooltip.hint")
  private val sessionDisposerRegisteredKey = Key<Unit>("intellij.platform.inline.completion.tooltip.disposer.registered")

  @RequiresEdt
  fun isShown(session: InlineCompletionSession): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    return tooltipHintKey.isIn(session.dataHolder)
  }

  @RequiresEdt
  fun show(session: InlineCompletionSession) {
    ThreadingAssertions.assertEventDispatchThread()
    if (isShown(session)) {
      return
    }

    val editor = session.context.editor

    if (!application.currentSession.isLocal && !System.getProperty("inline.completion.tooltip.remote.dev").toBoolean()) {
      return
    }

    val activeLookup = LookupManager.getActiveLookup(editor)
    if (activeLookup?.isPositionedAboveCaret == true) {
      return
    }

    val panel = InlineCompletionTooltipComponent().create(session)

    val hint = InlineCompletionTooltipHint(
      component = panel,
      onHide = { session.dataHolder.putUserData(tooltipHintKey, null) }
    )

    val offset = runReadActionBlocking { editor.caretModel.offset }
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
    session.dataHolder.putUserData(tooltipHintKey, hint)

    // The user may show/hide the tooltip many times during a session; register the disposer
    // only on the first show so we don't accumulate dead disposables on the session.
    if (!sessionDisposerRegisteredKey.isIn(session.dataHolder)) {
      session.dataHolder.putUserData(sessionDisposerRegisteredKey, Unit)
      Disposer.register(session) {
        session.dataHolder.getUserData(tooltipHintKey)?.hide()
        session.dataHolder.removeUserData(sessionDisposerRegisteredKey)
        session.dataHolder.removeUserData(tooltipHintKey)
      }
    }
  }

  @RequiresEdt
  fun hide(session: InlineCompletionSession) {
    ThreadingAssertions.assertEventDispatchThread()
    // The user data is removed in the `onPopupCancel`
    session.dataHolder.getUserData(tooltipHintKey)?.hide()
  }

  private val InlineCompletionSession.dataHolder: UserDataHolder
    get() = request

  /**
   * The hint used by [InlineCompletionTooltip].
   *
   * Vetoes hiding for one EDT tick after being shown. Without this, a platform-level listener
   * (e.g. one of `HintManagerImpl`'s global AWT listeners) would react to the very same click that
   * opened the tooltip — or any click that immediately follows it — and close the hint before the
   * user gets to see it. The veto is released on the next EDT tick, after the originating mouse
   * event has been fully processed.
   *
   * On cancel, invokes [onHide] and reports the tooltip's visible duration to
   * [InlineCompletionOnboardingComponent] exactly once — `onPopupCancel` may fire multiple times.
   */
  private class InlineCompletionTooltipHint(
    component: JComponent,
    private val onHide: () -> Unit,
  ) : LightweightHint(component) {
    private val hintShownMs = System.currentTimeMillis()
    private var hintTimeRegistered = false
    private var myVetoesHiding = true

    init {
      setForceShowAsPopup(true)
      setBelongsToGlobalPopupStack(false)

      // Release the veto on the next EDT tick — by then the mouse event that opened the hint has been fully processed.
      application.invokeLater {
        myVetoesHiding = false
      }
    }

    override fun vetoesHiding(): Boolean {
      return super.vetoesHiding() || myVetoesHiding
    }

    override fun onPopupCancel() {
      onHide()

      if (!hintTimeRegistered) {
        val hintHiddenMs = System.currentTimeMillis()
        InlineCompletionOnboardingComponent.getInstance().fireTooltipLivedFor(hintHiddenMs - hintShownMs)
        hintTimeRegistered = true
      }
    }
  }
}
