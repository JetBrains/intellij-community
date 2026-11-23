// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.ui.icons.EMPTY_ICON
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class LineMarkersConcurrencyTest {
  private val projectFixture: TestFixture<Project> = projectFixture()

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  fun `concurrent updates do not duplicate line markers`(incremental: Boolean): Unit = timeoutRunBlocking(timeout = 30.seconds) {
    val project = projectFixture.get()
    val (file, session) = readAction {
      val file = PsiFileFactory.getInstance(project).createFileFromText("markers.txt", PlainTextFileType.INSTANCE, "text")
      file to HighlightingSessionImpl.getOrCreateHighlightingSession(file, DaemonProgressIndicator(), ProperTextRange(0, file.textLength))
    }
    val document = session.document
    val creatingRenderer = CountDownLatch(1)
    val continueUpdate = CountDownLatch(1)
    val firstMarker = readAction {
      marker(file) {
        creatingRenderer.countDown()
        assertTrue(continueUpdate.await(10, TimeUnit.SECONDS))
      }
    }
    val secondMarker = readAction { marker(file) }
    val firstTask = FutureTask {
      ReadAction.runBlocking<RuntimeException> {
        LineMarkersUtil.setLineMarkersToEditor(project, document, file.textRange, listOf(firstMarker), Pass.LINE_MARKERS, session)
      }
    }
    val secondTask = FutureTask {
      ReadAction.runBlocking<RuntimeException> {
        if (incremental) {
          LineMarkersUtil.addLineMarkerToEditorIncrementally(project, document, secondMarker, session)
        }
        else {
          LineMarkersUtil.setLineMarkersToEditor(project, document, file.textRange, listOf(secondMarker), Pass.LINE_MARKERS, session)
        }
      }
    }
    val firstThread = Thread(firstTask, "first line marker update").apply { isDaemon = true }
    val secondThread = Thread(secondTask, "second line marker update").apply { isDaemon = true }
    try {
      firstThread.start()
      withContext(Dispatchers.IO) { assertTrue(creatingRenderer.await(10, TimeUnit.SECONDS)) }
      secondThread.start()
      while (!secondTask.isDone &&
             !(secondThread.state == Thread.State.BLOCKED &&
               secondThread.stackTrace.firstOrNull()?.className == LineMarkersUtil::class.java.name)) {
        delay(1.milliseconds)
      }
    }
    finally {
      continueUpdate.countDown()
      withContext(NonCancellable + Dispatchers.IO) {
        firstTask.get(10, TimeUnit.SECONDS)
        if (secondThread.state != Thread.State.NEW) {
          secondTask.get(10, TimeUnit.SECONDS)
        }
      }
    }
    readAction {
      assertEquals(1, LineMarkersPass.getDisplayedLineMarkers(document, project).size)
    }
  }

  private fun marker(file: PsiFile, beforeRenderer: () -> Unit = {}): LineMarkerInfo<PsiFile> =
    object : LineMarkerInfo<PsiFile>(file, file.textRange, EMPTY_ICON, null, null, GutterIconRenderer.Alignment.CENTER, { "marker" }) {
      override fun createGutterRenderer(): GutterIconRenderer? {
        beforeRenderer()
        return super.createGutterRenderer()
      }
    }
}
