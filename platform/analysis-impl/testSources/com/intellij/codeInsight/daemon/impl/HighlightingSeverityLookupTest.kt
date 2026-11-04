// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDirectory
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
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class HighlightingSeverityLookupTest {
  companion object {
    private val projectFixture: TestFixture<Project> = projectFixture()
    private val sourceRoot: TestFixture<PsiDirectory> = projectFixture.moduleFixture().sourceRootFixture()
    private const val GROUP: Int = 123
  }

  private val fileFixture: TestFixture<PsiFile> = sourceRoot.psiFileFixture("test.txt", "text")
  private val editorFixture: TestFixture<Editor> = fileFixture.editorFixture()

  @Test
  fun `processing highlights without a severity filter does not read the inspection profile`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      withHighlight(disposable) { project, editor, info, _ ->
        val highlights = ArrayList<HighlightInfo>()
        val completed = DaemonCodeAnalyzerEx.processHighlights(
          editor.markupModel as MarkupModelEx, project, null, 0, editor.document.textLength, anyContext(),
        ) {
          highlights.add(it)
          true
        }

        assertTrue(completed)
        assertEquals(listOf(info), highlights)
      }
    }

  @Test
  @Suppress("DEPRECATION")
  fun `clearing highlights does not read the inspection profile`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withHighlight(disposable) { project, editor, _, highlighter ->
      UpdateHighlightersUtil.setHighlightersToSingleEditor(
        project, editor, 0, editor.document.textLength, emptyList(), editor.colorsScheme, GROUP,
      )

      assertFalse(highlighter.isValid)
      assertTrue(editor.markupModel.allHighlighters.isEmpty())
    }
  }

  private suspend fun withHighlight(
    disposable: Disposable,
    action: (Project, Editor, HighlightInfo, RangeHighlighterEx) -> Unit,
  ) {
    val project = projectFixture.get()
    val editor = editorFixture.get()
    withContext(Dispatchers.EDT) {
      project.replaceService(
        InspectionProjectProfileManager::class.java,
        object : InspectionProjectProfileManager() {
          override fun getCurrentProfile(): InspectionProfileImpl = error("The inspection profile must not be read")

          override fun getProfiles(): Collection<InspectionProfileImpl> = error("The inspection profiles must not be read")

          override fun setRootProfile(name: String?): Unit = error("The inspection profile must not change")

          override fun getProfile(name: String, returnRootProfileIfNamedIsAbsent: Boolean): InspectionProfileImpl =
            error("The inspection profile must not be read")

          override fun getSeverityRegistrar(): SeverityRegistrar = error("The severity registrar must not be read")
        },
        disposable,
      )
      val info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).range(0, 1).group(GROUP).createUnconditionally()
      val highlighter = editor.markupModel.addRangeHighlighter(
        0, 1, HighlighterLayer.ADDITIONAL_SYNTAX, null, HighlighterTargetArea.EXACT_RANGE,
      ) as RangeHighlighterEx
      BackgroundUpdateHighlightersUtil.associateInfoAndHighlighter(info, highlighter)
      action(project, editor, info, highlighter)
    }
  }
}
