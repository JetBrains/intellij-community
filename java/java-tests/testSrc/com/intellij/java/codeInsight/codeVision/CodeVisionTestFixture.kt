// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionInitializer
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionInlayRenderer
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.BasePlatformTestCase.assertEquals
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CodeVisionTestFixture(
  private val editor: Editor,
  private val virtualFile: VirtualFile,
  private val project: Project,
  private val disposable: Disposable,
  private val onlyCodeVisionHintsAllowed: Boolean = false,
) {

  fun doHighlighting(file: PsiFile): List<HighlightInfo> =
    CodeInsightTestFixtureImpl.instantiateAndRun(file, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false, false)

  suspend fun testProviders(context: CodeInsightContext, expectedText: String, vararg enabledProviderGroupIds: String) {
    val sourceText = InlayDumpUtil.removeInlays(expectedText)
    project.waitForSmartMode()
    val codeVisionHost = readAction { CodeVisionInitializer.getInstance(project).getCodeVisionHost() }

    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val settings = CodeVisionSettings.getInstance()
        val file = requireNotNull(PsiManager.getInstance(project).findFile(virtualFile, context))
        codeVisionHost.providers.map { it.groupId }.toSet().forEach {
          settings.setProviderEnabled(it, enabledProviderGroupIds.contains(it))
        }
        TestModeFlags.set(CodeVisionHost.isCodeVisionTestKey, true, disposable)
        codeVisionHost.providers.forEach {
          if (it.id == "vcs.code.vision" && enabledProviderGroupIds.contains(it.groupId)) {
            it.preparePreview(editor, file)
          }
        }
        val document =
          requireNotNull(FileDocumentManager.getInstance().getDocument(virtualFile))
        WriteCommandAction.runWriteCommandAction(project) {
          document.setText(sourceText)
          PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        }
        val newFile = requireNotNull(PsiDocumentManager.getInstance(project).getPsiFile(document, context))
        doHighlighting(newFile)
        codeVisionHost.calculateCodeVisionSync(editor, disposable)
      }
    }
    assertText(expectedText)
  }

  private suspend fun assertText(expectedText: String) {
    val sourceText = InlayDumpUtil.removeInlays(expectedText)
    val actualText = withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        dumpCodeVisionHints(sourceText)
      }
    }
    assertEquals(expectedText, actualText)
  }


  private fun dumpCodeVisionHints(sourceText: String): String {
    return InlayDumpUtil.dumpInlays(
      sourceText, editor,
      filter = {
        val rendererSupported = it.renderer is CodeVisionInlayRenderer
        if (onlyCodeVisionHintsAllowed && !rendererSupported) error("renderer not supported")
        rendererSupported
      },
      renderer = { _, inlay ->
        inlay.getUserData(CodeVisionListData.KEY)!!.visibleLens.joinToString(prefix = "[", postfix = "]", separator = "   ") { it.longPresentation }
      })
  }
}

fun codeVisionFixture(editorFixture: TestFixture<Editor>, fileFixture: TestFixture<PsiFile>): TestFixture<CodeVisionTestFixture> =
  testFixture("code-vision-fixture") {
    val disposable = Disposer.newDisposable()
    val editor = editorFixture.init()
    val file = fileFixture.init()

    val codeVisionFixture = CodeVisionTestFixture(editor, file.virtualFile, file.project, disposable)

    initialized(codeVisionFixture) {
      // cleanup
      Disposer.dispose(disposable)
    }

  }