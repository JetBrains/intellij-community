// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.replaceService
import com.intellij.ui.docking.DockManager
import com.intellij.util.ArrayUtilRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.SwingConstants

@TestApplication
internal class SplitEditorProblemsTest {
  companion object {
    private val projectFixture = projectProblemsProjectFixture()
  }

  private val problems by projectProblemsFixture(projectFixture)
  private val myFixture get() = problems.fixture
  private var manager: FileEditorManagerImpl? = null

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    val project = myFixture.project
    project.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true)
    project.putUserData(CodeVisionHost.isCodeVisionTestKey, true)
    withContext(Dispatchers.EDT) {
      manager = FileEditorManagerImpl(project, (project as ComponentManagerEx).getCoroutineScope().childScope()).also { it.initDockableContentFactory() }
      project.replaceService(FileEditorManager::class.java, manager!!, myFixture.testRootDisposable)
      (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders()
    }
  }

  @Test
  fun testClassRenameInTwoDetachedWindows(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      try {
        val parentClass = myFixture.addClass("""
          package bar;
          
          public class Parent1 {
          }
        """.trimIndent())
        val childClass = myFixture.addClass("""
          package foo;
          
          import bar.Parent1;
    
          public final class Child extends Parent1 {
          }
        """.trimIndent())

        val editorManager = manager!!
        editorManager.openFileInNewWindow(childClass.containingFile.virtualFile).first[0]
        val parentEditor = (editorManager.openFileInNewWindow(parentClass.containingFile.virtualFile).first[0] as TextEditor).editor
        rehighlight(parentEditor)

        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
          parentClass.nameIdentifier?.replace(factory.createIdentifier("Parent"))
        }
        rehighlight(parentEditor)
        assertThat(problems.getProblems(parentEditor)).hasSize(2)
      }
      finally {
        DockManager.getInstance(myFixture.project).containers.forEach { it.closeAll() }
      }
    }
  }

  @Test
  fun testRenameClassChangeUsageAndUndoAllInSplitEditor(): Unit = timeoutRunBlocking {
    val parentClass = myFixture.addClass("""
      package bar;
      
      public class Parent1 {
      }
    """.trimIndent())
    val childClass = myFixture.addClass("""
      package foo;
      
      import bar.Parent1;

      public final class Child extends Parent1 {
      }
    """.trimIndent())

    // open parent class and rehighlight
    withContext(Dispatchers.EDT) {
      val editorManager = manager!!
      val parentTextEditor = editorManager.openFile(parentClass.containingFile.virtualFile, true)[0] as TextEditor
      val parentEditor = parentTextEditor.editor
      rehighlight(parentEditor)
      assertThat(problems.getProblems(parentEditor)).isEmpty()

      // open child class in horizontal split, focus stays in the parent editor
      val currentWindow = editorManager.currentWindow!!
      editorManager.createSplitter(SwingConstants.HORIZONTAL, currentWindow)
      val nextWindow = editorManager.getNextWindow(currentWindow)!!
      val childEditor = editorManager.openFile(file = childClass.containingFile.virtualFile, nextWindow).allEditors.first()

      // rename parent, check for errors
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
        parentClass.nameIdentifier?.replace(factory.createIdentifier("Parent"))
      }
      rehighlight(parentEditor)
      assertThat(problems.getProblems(parentEditor)).hasSize(2)

      // select child editor, remove parent from a child extends list, check that the number of problems changed
      IdeFocusManager.getInstance(myFixture.project).requestFocus(childEditor.component, true)
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        val factory = JavaPsiFacade.getInstance(myFixture.project).elementFactory
        childClass.extendsList?.replace(factory.createReferenceList(PsiJavaCodeReferenceElement.EMPTY_ARRAY))
      }
      rehighlight(parentEditor)
      assertThat(problems.getProblems(parentEditor)).hasSize(1)

      // undo child change
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        UndoManager.getInstance(myFixture.project).undo(childEditor)
      }
      rehighlight(parentEditor)
      assertThat(problems.getProblems(parentEditor)).hasSize(2)

      // undo parent rename, check that problems are gone
      IdeFocusManager.getInstance(myFixture.project).requestFocus(parentEditor.component, true)
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        UndoManager.getInstance(myFixture.project).undo(parentTextEditor)
      }
      rehighlight(parentEditor)
      assertThat(problems.getProblems(parentEditor)).isEmpty()
    }
  }

  private fun rehighlight(editor: Editor) {
    val psiFile = PsiDocumentManager.getInstance(myFixture.project).getPsiFile(editor.document)
    CodeInsightTestFixtureImpl.instantiateAndRun(psiFile!!, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false)
  }
}
