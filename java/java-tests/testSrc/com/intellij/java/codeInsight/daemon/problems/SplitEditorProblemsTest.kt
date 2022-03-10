// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.registerComponentInstance
import com.intellij.ui.docking.DockManager
import com.intellij.util.ArrayUtilRt
import javax.swing.SwingConstants

internal class SplitEditorProblemsTest : ProjectProblemsViewTest() {

  private var myManager: FileEditorManagerImpl? = null

  override fun setUp() {
    super.setUp()
    project.putUserData(CodeVisionHost.isCodeVisionTestKey, true)
    myManager = FileEditorManagerImpl(project).also { it.initDockableContentFactory() }
    project.registerComponentInstance(FileEditorManager::class.java, myManager!!, testRootDisposable)
    (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders()
  }

  override fun tearDown() {
    project.putUserData(CodeVisionHost.isCodeVisionTestKey, null)
    super.tearDown()
  }

  fun testClassRenameInTwoDetachedWindows() {
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

    val editorManager = myManager!!
    editorManager.openFileInNewWindow(childClass.containingFile.virtualFile).first[0]
    val parentEditor = (editorManager.openFileInNewWindow(parentClass.containingFile.virtualFile).first[0] as TextEditorImpl).editor
    rehighlight(parentEditor)

    WriteCommandAction.runWriteCommandAction(project) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory
      parentClass.nameIdentifier?.replace(factory.createIdentifier("Parent"))
    }
    rehighlight(parentEditor)
    assertSize(2, getProblems(parentEditor))

    DockManager.getInstance(project).containers.forEach { it.closeAll() }
  }

  fun testRenameClassChangeUsageAndUndoAllInSplitEditor() {
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
    val editorManager = myManager!!
    val parentTextEditor = editorManager.openFile(parentClass.containingFile.virtualFile, true)[0] as TextEditorImpl
    val parentEditor = parentTextEditor.editor
    rehighlight(parentEditor)
    assertEmpty(getProblems(parentEditor))

    // open child class in horizontal split, focus stays in parent editor
    val currentWindow = editorManager.currentWindow
    editorManager.createSplitter(SwingConstants.HORIZONTAL, currentWindow)
    val nextWindow = editorManager.getNextWindow(currentWindow)
    val childEditor = editorManager.openFileWithProviders(childClass.containingFile.virtualFile, false, nextWindow).first[0]

    // rename parent, check for errors
    WriteCommandAction.runWriteCommandAction(project) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory
      parentClass.nameIdentifier?.replace(factory.createIdentifier("Parent"))
    }
    rehighlight(parentEditor)
    assertSize(2, getProblems(parentEditor))

    // select child editor, remove parent from child extends list, check that number of problems changed
    IdeFocusManager.getInstance(project).requestFocus(childEditor.component, true)
    WriteCommandAction.runWriteCommandAction(project) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory
      childClass.extendsList?.replace(factory.createReferenceList(PsiJavaCodeReferenceElement.EMPTY_ARRAY))
    }
    rehighlight(parentEditor)
    assertSize(1, getProblems(parentEditor))

    // undo child change
    WriteCommandAction.runWriteCommandAction(project) {
      UndoManager.getInstance(project).undo(childEditor)
    }
    rehighlight(parentEditor)
    assertSize(2, getProblems(parentEditor))

    // undo parent rename, check that problems are gone
    IdeFocusManager.getInstance(project).requestFocus(parentEditor.component, true)
    WriteCommandAction.runWriteCommandAction(project) {
      UndoManager.getInstance(project).undo(parentTextEditor)
    }
    rehighlight(parentEditor)
    assertEmpty(getProblems(parentEditor))
  }

  private fun rehighlight(editor: Editor) {
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
    CodeInsightTestFixtureImpl.instantiateAndRun(psiFile!!, editor, ArrayUtilRt.EMPTY_INT_ARRAY, false)
  }
}