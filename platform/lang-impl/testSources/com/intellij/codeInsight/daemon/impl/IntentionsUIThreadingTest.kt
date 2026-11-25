// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.intention.impl.CachedIntentions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.useBackgroundWriteAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
internal class IntentionsUIThreadingTest {
  private val projectFixture: TestFixture<Project> = projectFixture()

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `background invalidation preserves fresh intentions`(refreshCache: Boolean): Unit = timeoutRunBlocking {
    assumeTrue(useBackgroundWriteAction)
    val project = projectFixture.get()
    val application = ApplicationManager.getApplication()
    val factory = EditorFactory.getInstance()
    val (editor, file) = withContext(Dispatchers.EDT) {
      factory.createEditor(factory.createDocument("text"), project) to
        PsiFileFactory.getInstance(project).createFileFromText("intentions.txt", PlainTextFileType.INSTANCE, "text")
    }
    val hideCalls = AtomicInteger()
    val expected = if (refreshCache) 0 else 1
    val edtHidden = CompletableDeferred<Unit>()
    val ui = object : IntentionsUI(project) {
      override fun update(cachedIntentions: CachedIntentions, actionsChanged: Boolean) {}

      override fun hide() {
        application.assertIsDispatchThread()
        if (hideCalls.incrementAndGet() == expected + 1) {
          edtHidden.complete(Unit)
        }
      }
    }
    try {
      val drained = CompletableDeferred<Unit>()
      backgroundWriteAction {
        assertFalse(application.isDispatchThread)
        val oldCache = ui.getCachedIntentions(editor, file)
        ui.invalidate()
        assertNotSame(oldCache, ui.getCachedIntentions(editor, file))
        ui.invalidate()
        ui.invalidate()
        if (refreshCache) {
          ui.getCachedIntentions(editor, file)
        }
        assertEquals(0, hideCalls.get())
        application.invokeLater({ drained.complete(Unit) }, ModalityState.defaultModalityState(), project.disposed)
      }
      drained.await()
      assertEquals(expected, hideCalls.get())
      withContext(Dispatchers.EDT) {
        ui.invalidate()
        ui.invalidate()
        assertEquals(expected, hideCalls.get())
      }
      edtHidden.await()
      assertEquals(expected + 1, hideCalls.get())
    }
    finally {
      withContext(Dispatchers.EDT) { factory.releaseEditor(editor) }
    }
  }
}
