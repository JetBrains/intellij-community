// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.SkipInHeadlessEnvironment
import kotlinx.coroutines.runBlocking
import java.awt.Dialog
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.JDialog

/**
 * A background write action restarts an auto-popup completion whose editor sits in a modal context.
 *
 * The stop event of the completion runs in the modality of the editor component.
 * The restart listener of `BgCalculation` must queue the restart with the same modality.
 * A restart with a weaker modality waits until the modal state ends.
 * The stop event then runs first, and the restart expires.
 *
 * The test needs a real window for the modality of the editor component, so it cannot run headless.
 * The window never shows, so the restarted completion cannot show the lookup.
 * The test therefore checks the restarted completion pass, not the lookup.
 */
@SkipInHeadlessEnvironment
class CompletionBackgroundWriteActionModalityTest : JavaCompletionAutoPopupTestCase() {
  private var modalDialog: JDialog? = null

  fun testBackgroundWriteActionRestartsAutoPopupInModalContext() {
    myFixture.configureByText("a.java", "class C { void foo() { <caret> } }")
    val state = ModalRestartState()
    registerContributor(state)

    typeOnEdt('x')
    assertTrue("The contributor did not start", state.contributorEntered.await(30, TimeUnit.SECONDS))
    enterModalContext()
    try {
      runBlocking {
        backgroundWriteAction { }
      }
      val restarted = awaitSecondPass(state)
      assertTrue("The restart did not run in the modal context, phase=${CompletionServiceImpl.completionPhase}", restarted)
    }
    finally {
      leaveModalContext()
    }
  }

  // Keep the lambdas out of the test method. The JUnit 3 runner rejects the synthetic `test…$lambda` methods.

  private fun registerContributor(state: ModalRestartState) {
    ModalContributor.sharedState = state
    Disposer.register(testRootDisposable) { ModalContributor.sharedState = null }
    val ep = CompletionContributorEP("any", ModalContributor::class.java.name, DefaultPluginDescriptor("IJPL-260786-modality-test"))
    CompletionContributor.EP.point.registerExtension(ep, LoadingOrder.FIRST, testRootDisposable)
  }

  private fun typeOnEdt(c: Char) {
    ApplicationManager.getApplication().invokeAndWait { myFixture.type(c) }
  }

  /** Puts the editor component into a modal dialog and enters its modal state. The dialog never shows. */
  private fun enterModalContext() {
    ApplicationManager.getApplication().invokeAndWait {
      val dialog = JDialog(null as Dialog?, true)
      dialog.contentPane.add(myFixture.editor.contentComponent)
      modalDialog = dialog
      LaterInvocator.enterModal(dialog)
    }
  }

  private fun leaveModalContext() {
    ApplicationManager.getApplication().invokeAndWait(
      {
        val dialog = modalDialog ?: return@invokeAndWait
        modalDialog = null
        LaterInvocator.leaveModal(dialog)
        dialog.contentPane.remove(myFixture.editor.contentComponent)
        dialog.dispose()
      },
      ModalityState.any())
  }

  /** Waits until the restarted completion runs the contributor again, or until the stop event ends the session. */
  private fun awaitSecondPass(state: ModalRestartState): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
    while (System.nanoTime() < deadline) {
      if (state.secondPassStarted.count == 0L) return true
      if (CompletionServiceImpl.completionPhase is CompletionPhase.EmptyAutoPopup) return false
      Thread.sleep(50)
    }
    return false
  }

  private class ModalRestartState {
    val contributorEntered: CountDownLatch = CountDownLatch(1)
    val secondPassStarted: CountDownLatch = CountDownLatch(1)

    @Volatile
    var firstPass: Boolean = true
  }

  /** On the first pass, adds no item and runs until the cancellation. On the restart pass, adds [MODAL_RESTART_ITEM]. */
  private class ModalContributor : CompletionContributor(), DumbAware {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
      val state = sharedState ?: return
      if (!state.firstPass) {
        state.secondPassStarted.countDown()
        result.addElement(LookupElementBuilder.create(MODAL_RESTART_ITEM))
        return
      }
      state.firstPass = false
      state.contributorEntered.countDown()
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
      while (System.nanoTime() < deadline) {
        ProgressManager.checkCanceled()
        Thread.sleep(10)
      }
    }

    companion object {
      @JvmStatic
      @Volatile
      var sharedState: ModalRestartState? = null
    }
  }
}

private const val MODAL_RESTART_ITEM = "xModalRestartItem"
