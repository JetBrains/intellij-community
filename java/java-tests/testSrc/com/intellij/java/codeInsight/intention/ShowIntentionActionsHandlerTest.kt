// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.Companion.lightDaemonAnalyzerFixture
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class ShowIntentionActionsHandlerTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())
  }

  private val daemonFixture = lightDaemonAnalyzerFixture(projectFixture)
  private val myFixture get() = daemonFixture.get().fixture

  @Test
  fun testSelectionOverlapsInjection(): Unit = timeoutRunBlocking {
    daemonFixture.get().configureFromFileText("A.java", "class A{ { java.util.regex.Pattern.compile(<caret><selection>\"pat</selection>tern\") } }")
    val editor = myFixture.editor
    val file = myFixture.file
    doTest(editor, file) {
      assertEquals(1, it.size)
      assertEquals(editor to file, it.single())
    }
  }

  @Test
  fun testSelectionInsideInjection(): Unit = timeoutRunBlocking {
    daemonFixture.get().configureFromFileText("A.java", "class A{ { java.util.regex.Pattern.compile(\"<caret><selection>pat</selection>tern\") } }")
    val editor = myFixture.editor
    val file = myFixture.file
    assertInstanceOf(EditorWindow::class.java, editor)
    val injectedLanguageManager = InjectedLanguageManager.getInstance(myFixture.project)
    assertTrue(readAction { injectedLanguageManager.isInjectedFragment(file) })
    doTest(editor, file) {
      assertEquals(1, it.size)
      assertEquals(editor to file, it.single())
    }
  }

  @Test
  fun testHostEditorAndFileWithSelectionInsideInjection(): Unit = timeoutRunBlocking {
    daemonFixture.get().configureFromFileText("A.java", "class A{ { java.util.regex.Pattern.compile(\"<caret><selection>pat</selection>tern\") } }")
    val editor = myFixture.editor
    val file = myFixture.file
    assertInstanceOf(EditorWindow::class.java, editor)
    val injectedLanguageManager = InjectedLanguageManager.getInstance(myFixture.project)
    assertTrue(readAction { injectedLanguageManager.isInjectedFragment(file) })
    val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
    val hostFile = readAction { injectedLanguageManager.getInjectionHost(file)!!.containingFile }
    doTest(hostEditor, hostFile) {
      assertEquals(2, it.size)
    }
  }

  private suspend fun doTest(editor: Editor, file: PsiFile, also: (Collection<Pair<Editor, PsiFile>>) -> Unit = {}) {
    val testIntention = AssertingTestIntention { project, editor, file ->
      assertTrue(InjectedLanguageManager.getInstance(project).isInjectedFragment(file) == (editor is EditorWindow),
                 "Injected file or editor was provided alongside host editor or file: $editor, $file")
    }
    withContext(Dispatchers.EDT) {
      ShowIntentionActionsHandler.chooseActionAndInvoke(file, editor, testIntention, "test")
    }
    also(testIntention.editorToFile)
  }

  class AssertingTestIntention(val editorToFile: MutableCollection<Pair<Editor, PsiFile>> = mutableSetOf(),
                               val assertionOnIsAvailable: (Project, Editor, PsiFile) -> Unit) : IntentionAction {
    override fun getText(): String {
      return "test"
    }

    override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
      assertionOnIsAvailable(project, editor, psiFile)
      editorToFile.add(editor to psiFile)
      return false
    }

    override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
    }

    override fun startInWriteAction(): Boolean {
      return false
    }

    override fun getFamilyName(): String {
      return "test"
    }
  }
}
