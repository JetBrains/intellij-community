// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.editor.markup.UIController
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.util.ui.EmptyIcon
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class ErrorStripeUpdateManagerTest {
  private val projectFixture: TestFixture<Project> = projectFixture(openAfterCreation = true)
  private val fileFixture: TestFixture<PsiFile> = projectFixture.moduleFixture().sourceRootFixture().psiFileFixture("status.txt", "text")
  private val editorFixture: TestFixture<Editor> = fileFixture.editorFixture()

  @Test
  fun `a contributor can leave a suspended status without a daemon pass`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val editor = editorFixture.get()
    val title = AtomicReference("Suspended")
    val renderer = readAction {
      object : TrafficLightRenderer(project, editor) {
        override fun getStatus(): AnalyzerStatus = AnalyzerStatus(EmptyIcon.ICON_16, title.get(), "", UIController.EMPTY)

        override fun isValid(): Boolean = true
      }
    }
    val markup = editor.markupModel as EditorMarkupModelImpl
    withContext(Dispatchers.EDT) {
      markup.isErrorStripeVisible = true
      markup.setErrorStripeRenderer(renderer)
    }
    awaitTitle(markup, "Suspended")

    val notified = CompletableDeferred<Unit>()
    project.messageBus.connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      override fun daemonStatusChanged() {
        notified.complete(Unit)
      }
    })

    title.set("Ready")
    ErrorStripeUpdateManager.getInstance(project).notifyTrafficLightStatusChanged()

    notified.await()
    awaitTitle(markup, "Ready")
  }

  @Test
  fun `status notification works with a hidden error stripe`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val markup = editorFixture.get().markupModel as EditorMarkupModelImpl
    withContext(Dispatchers.EDT) { markup.isErrorStripeVisible = false }
    val notified = CompletableDeferred<Unit>()
    project.messageBus.connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      override fun daemonStatusChanged() {
        notified.complete(Unit)
      }
    })

    ErrorStripeUpdateManager.getInstance(project).notifyTrafficLightStatusChanged()

    notified.await()
    assertFalse(withContext(Dispatchers.EDT) { markup.isErrorStripeVisible })
  }

  private suspend fun awaitTitle(markup: EditorMarkupModelImpl, expected: String) {
    while (withContext(Dispatchers.EDT) { markup.getCurrentStatus().title } != expected) {
      delay(1.milliseconds)
    }
    assertEquals(expected, withContext(Dispatchers.EDT) { markup.getCurrentStatus().title })
  }
}
