// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.InjectedDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.get
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent

@TestApplication
@Timeout(30)
class TextEditorPsiDataRuleTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  @Test
  fun `an absent injection reuses the host target`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withEditor { editor, file ->
      val computations = replaceTargetResolver(disposable) { file }
      val context = createContext(editor)
      val injected = AnActionEvent.getInjectedDataContext(context)
      readAction {
        assertThat(context[CommonDataKeys.PSI_ELEMENT]).isSameAs(file)
        assertThat(injected[CommonDataKeys.PSI_ELEMENT]).isSameAs(file)
        assertThat(computations.get()).isEqualTo(1)
      }
    }
  }

  @Test
  fun `an injected target takes precedence`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withEditor { editor, file ->
      val computations = replaceTargetResolver(disposable) { file }
      val injectedTarget = readAction { file.firstChild }
      val context = createContext(editor, injectedTarget)
      readAction {
        assertThat(AnActionEvent.getInjectedDataContext(context)[CommonDataKeys.PSI_ELEMENT]).isSameAs(injectedTarget)
        assertThat(computations.get()).isZero()
        assertThat(context[CommonDataKeys.PSI_ELEMENT]).isSameAs(file)
        assertThat(computations.get()).isEqualTo(1)
      }
    }
  }

  @Test
  fun `the fallback keeps the host target when an injected editor has no target`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    withEditor { hostEditor, hostFile ->
      withEditor { injectedEditor, injectedFile ->
        replaceTargetResolver(disposable) { editor -> if (editor === injectedEditor) injectedFile else hostFile }
        val context = createContext(hostEditor, injectedEditor = injectedEditor)
        readAction {
          assertThat(AnActionEvent.getInjectedDataContext(context)[CommonDataKeys.PSI_ELEMENT]).isSameAs(hostFile)
          assertThat(context[CommonDataKeys.PSI_ELEMENT]).isSameAs(hostFile)
        }
      }
    }
  }

  private fun replaceTargetResolver(disposable: Disposable, resolve: (Editor) -> PsiElement?): AtomicInteger {
    val computations = AtomicInteger()
    application.replaceService(TargetElementUtil::class.java, object : TargetElementUtil() {
      override fun findTargetElement(editor: Editor, flags: Int, offset: Int): PsiElement? {
        computations.incrementAndGet()
        return resolve(editor)
      }
    }, disposable)
    return computations
  }

  private suspend fun createContext(editor: Editor, injectedTarget: PsiElement? = null, injectedEditor: Editor? = null): DataContext =
    withContext(Dispatchers.EDT) {
      val component = object : JComponent(), UiDataProvider {
        override fun uiDataSnapshot(sink: DataSink) {
          sink[CommonDataKeys.EDITOR] = editor
          sink[CommonDataKeys.PROJECT] = editor.project
          sink.lazy(InjectedDataKeys.PSI_ELEMENT) { injectedTarget }
          sink[InjectedDataKeys.EDITOR] = injectedEditor
          sink[InjectedDataKeys.CARET] = injectedEditor?.caretModel?.primaryCaret
        }
      }
      Utils.createAsyncDataContext(component)
    }

  private suspend inline fun withEditor(crossinline action: suspend CoroutineScope.(Editor, PsiFile) -> Unit) {
    val (editor, file) = withContext(Dispatchers.EDT) {
      val project = projectFixture.get()
      val file = PsiFileFactory.getInstance(project).createFileFromText("sample.txt", PlainTextFileType.INSTANCE, "text", 0, true)
      val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
      val editor = EditorFactory.getInstance().createEditor(document, project, file.virtualFile, false) as EditorEx
      editor.setFile(file.virtualFile)
      editor to file
    }
    try {
      coroutineScope { action(editor, file) }
    }
    finally {
      withContext(Dispatchers.EDT) {
        EditorFactory.getInstance().releaseEditor(editor)
      }
    }
  }
}
