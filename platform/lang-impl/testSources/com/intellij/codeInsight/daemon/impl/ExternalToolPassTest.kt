// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.lang.ExternalLanguageAnnotators
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ProperTextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.CompletableDeferred
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock

@TestApplication
@Suppress("SuspiciousPackagePrivateAccess")
internal class ExternalToolPassTest {
  private val projectFixture: TestFixture<Project> = projectFixture()
  private val fileFixture: TestFixture<PsiFile> = projectFixture.moduleFixture().sourceRootFixture().psiFileFixture("external.txt", "text")

  @ParameterizedTest
  @EnumSource(Completion::class)
  fun `external completion is reported after the daemon finishes`(
    completion: Completion,
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val project = projectFixture.get()
    val file = fileFixture.get()
    suspendUntilIndexesAreReady(project)
    val queued = CompletableDeferred<Update>()
    val manager = mock(ExternalAnnotatorManager::class.java)
    doAnswer {
      assertTrue(queued.complete(it.getArgument(0)))
      null
    }.`when`(manager).queue(any(Update::class.java))
    ApplicationManager.getApplication().registerOrReplaceServiceInstance(ExternalAnnotatorManager::class.java, manager, disposable)

    var applied = false
    val annotator = object : ExternalAnnotator<PsiFile, Unit>() {
      override fun collectInformation(psiFile: PsiFile): PsiFile = psiFile

      override fun doAnnotate(collectedInfo: PsiFile) {
        if (completion == Completion.CANCELED) throw ProcessCanceledException()
      }

      override fun apply(psiFile: PsiFile, annotationResult: Unit, holder: AnnotationHolder) {
        applied = true
      }
    }
    ExternalLanguageAnnotators.INSTANCE.addExplicitExtension(file.language, annotator, disposable)
    val events = mutableListOf<String>()
    val statusChanged = CompletableDeferred<Unit>()
    project.messageBus.connect(disposable).subscribe(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC, object : DaemonCodeAnalyzer.DaemonListener {
      override fun daemonFinished(fileEditors: Collection<FileEditor>) {
        events += "daemon"
      }

      override fun daemonStatusChanged() {
        assertEquals(completion == Completion.FINISHED, applied)
        events += "external"
        statusChanged.complete(Unit)
      }
    })

    lateinit var pass: ExternalToolPass
    val indicator = DaemonProgressIndicator()
    ProgressManager.getInstance().runProcess({
      ReadAction.runBlocking<RuntimeException> {
        HighlightingSessionImpl.runInsideHighlightingSession(file, null, ProperTextRange.create(file.textRange), false) {
          pass = ExternalToolPass(file, file.viewProvider.document!!, null, 0, file.textLength, HighlightInfoProcessor.getEmpty()).apply {
            id = Pass.EXTERNAL_TOOLS
            setContext(anyContext())
            collectInformation(indicator)
          }
        }
      }
    }, indicator)
    val update = queued.await()
    project.messageBus.syncPublisher(DaemonCodeAnalyzer.DAEMON_EVENT_TOPIC).daemonFinished(emptyList())
    assertEquals(listOf("daemon"), events)

    when (completion) {
      Completion.FINISHED -> update.run()
      Completion.CANCELED -> assertThrows(ProcessCanceledException::class.java) { update.run() }
      Completion.REJECTED -> update.setRejected()
    }

    assertTrue(pass.infos.isEmpty())
    statusChanged.await()
    assertEquals(listOf("daemon", "external"), events)
  }

  enum class Completion { FINISHED, CANCELED, REJECTED }
}
