// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.impl.IntentionMenuContributor
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ConcurrencyUtil
import junit.framework.TestCase.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

class DoNotShowInspectionCancellationTest : BasePlatformTestCase() {

  fun testReadJobCancellationPropagatesThroughDoNotShowInspection() {
    myFixture.configureByText("test.txt", "hello world")

    val inspection = CancellationTestInspection()
    myFixture.enableInspections(inspection)
    val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
    val key = HighlightDisplayKey.find(CancellationTestInspection.SHORT_NAME)
    assertNotNull("inspection key should be registered", key)
    profile.setErrorLevel(key!!, HighlightDisplayLevel.DO_NOT_SHOW, project)

    val editor = myFixture.editor
    val file = myFixture.file
    val offset = editor.caretModel.offset

    val future = ApplicationManager.getApplication().executeOnPooledThread<Unit> {
      ReadAction.computeCancellable<Unit, Nothing> {
        inspection.readJobRef.set(Cancellation.currentJob())
        val intentions = ShowIntentionsPass.IntentionsInfo()
        for (contributor in IntentionMenuContributor.EP_NAME.extensionList) {
          contributor.collectActions(editor, file, intentions, -1, offset)
        }
      }
    }
    future.get(30, TimeUnit.SECONDS)

    assertTrue("inspection should have run", inspection.inspectionRan.get())
    assertTrue("inner coroutine should be cancelled when readJob is cancelled", inspection.innerCancelled.get())
  }
}

internal class CancellationTestInspection : LocalInspectionTool() {

  val readJobRef = AtomicReference<Job?>()
  val innerCancelled = AtomicBoolean(false)
  val inspectionRan = AtomicBoolean(false)

  companion object {
    const val SHORT_NAME = "CancellationTestDoNotShowInspection"
  }

  override fun getShortName(): String = SHORT_NAME
  override fun getDisplayName(): String = "Test: cancellation through DO_NOT_SHOW inspection"

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (!inspectionRan.compareAndSet(false, true)) return
        val readJob = readJobRef.get() ?: return
        try {
          runBlockingCancellable {
            readJob.cancel(CancellationException("simulating write action cancellation"))
            delay((ConcurrencyUtil.DEFAULT_TIMEOUT_MS * 2).milliseconds)
            fail("runBlockingCancellable was not cancelled")
          }
        }
        catch (_: CancellationException) {
          innerCancelled.set(true)
        }
      }
    }
  }
}
