// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention

import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler
import com.intellij.injected.editor.EditorWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.intellij.testFramework.LightJavaCodeInsightTestCase
import com.intellij.testFramework.UsefulTestCase
import org.junit.Assert

class ShowIntentionActionsHandlerTest : LightJavaCodeInsightTestCase() {

  fun testSelectionOverlapsInjection() {
    configureFromFileText("A.java", "class A{ { java.util.regex.Pattern.compile(<caret><selection>\"pat</selection>tern\") } }")
    doTest {
      UsefulTestCase.assertSize(1, it)
      Assert.assertEquals(editor to file, it.single())
    }
  }

  fun testSelectionInsideInjection() {
    configureFromFileText("A.java", "class A{ { java.util.regex.Pattern.compile(\"<caret><selection>pat</selection>tern\") } }")
    UsefulTestCase.assertInstanceOf(editor, EditorWindow::class.java)
    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    Assert.assertTrue(injectedLanguageManager.isInjectedFragment(file))
    doTest(editor, file) {
      UsefulTestCase.assertSize(1, it)
      Assert.assertEquals(editor to file, it.single())
    }
  }

  fun testHostEditorAndFileWithSelectionInsideInjection() {
    configureFromFileText("A.java", "class A{ { java.util.regex.Pattern.compile(\"<caret><selection>pat</selection>tern\") } }")
    UsefulTestCase.assertInstanceOf(editor, EditorWindow::class.java)
    val injectedLanguageManager = InjectedLanguageManager.getInstance(project)
    Assert.assertTrue(injectedLanguageManager.isInjectedFragment(file))
    val hostEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor)
    val hostFile = injectedLanguageManager.getInjectionHost(file)!!.containingFile
    doTest(hostEditor, hostFile) {
      UsefulTestCase.assertSize(2, it)
    }
  }

  private fun doTest(editor: Editor = this.editor, file: PsiFile = this.file, also: (Collection<Pair<Editor, PsiFile>>) -> Unit = {}) {
    val testIntention = AssertingTestIntention { project, editor, file ->
      Assert.assertTrue("Injected file or editor was provided alongside host editor or file: $editor, $file",
                        InjectedLanguageManager.getInstance(project).isInjectedFragment(file) == (editor is EditorWindow))
    }
    ShowIntentionActionsHandler.chooseActionAndInvoke(file, editor, testIntention, "test")
    also(testIntention.editorToFile)
  }

  class AssertingTestIntention(val editorToFile: MutableCollection<Pair<Editor, PsiFile>> = mutableSetOf(),
                               val assertionOnIsAvailable: (Project, Editor, PsiFile) -> Unit) : IntentionAction {
    override fun getText(): String {
      return "test"
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
      assertionOnIsAvailable(project, editor, file)
      editorToFile.add(editor to file)
      return false
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    }

    override fun startInWriteAction(): Boolean {
      return false
    }

    override fun getFamilyName(): String {
      return "test"
    }
  }
}
