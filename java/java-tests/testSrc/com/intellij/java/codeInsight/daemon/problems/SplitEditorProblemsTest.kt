// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.problems

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorProviderManagerImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.replaceService
import com.intellij.ui.docking.DockManager
import com.intellij.util.ArrayUtilRt
import javax.swing.SwingConstants

internal class SplitEditorProblemsTest : ProjectProblemsViewTest() {
  private var manager: FileEditorManagerImpl? = null

  override fun setUp() {
    super.setUp()
    val project = project
    project.putUserData(FileEditorManagerImpl.ALLOW_IN_LIGHT_PROJECT, true)
    project.putUserData(CodeVisionHost.isCodeVisionTestKey, true)
    manager = FileEditorManagerImpl(project, (project as ComponentManagerEx).getCoroutineScope().childScope()).also { it.initDockableContentFactory() }
    project.replaceService(FileEditorManager::class.java, manager!!, testRootDisposable)
    (FileEditorProviderManager.getInstance() as FileEditorProviderManagerImpl).clearSelectedProviders()

    Registry.get("ide.open.in.split.with.chooser.enabled").setValue(true, testRootDisposable)
  }

  override fun tearDown() {
    try {
      project.putUserData(CodeVisionHost.isCodeVisionTestKey, null)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testClassRenameInTwoDetachedWindows() {
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
      val parentEditor = (editorManager.openFileInNewWindow(parentClass.containingFile.virtualFile).first[0] as TextEditorImpl).editor
      rehighlight(parentEditor)

      WriteCommandAction.runWriteCommandAction(project) {
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        parentClass.nameIdentifier?.replace(factory.createIdentifier("Parent"))
      }
      rehighlight(parentEditor)
      assertSize(2, getProblems(parentEditor))
    }
    finally {
      DockManager.getInstance(project).containers.forEach { it.closeAll() }
    }
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
    val editorManager = manager!!
    val parentTextEditor = editorManager.openFile(parentClass.containingFile.virtualFile, true)[0] as TextEditorImpl
    val parentEditor = parentTextEditor.editor
    rehighlight(parentEditor)
    assertEmpty(getProblems(parentEditor))

    // open child class in horizontal split, focus stays in the parent editor
    val currentWindow = editorManager.currentWindow!!
    editorManager.createSplitter(SwingConstants.HORIZONTAL, currentWindow)
    val nextWindow = editorManager.getNextWindow(currentWindow)!!
    val childEditor = editorManager.openFile(file = childClass.containingFile.virtualFile, nextWindow).allEditors.first()

    // rename parent, check for errors
    WriteCommandAction.runWriteCommandAction(project) {
      val factory = JavaPsiFacade.getInstance(project).elementFactory
      parentClass.nameIdentifier?.replace(factory.createIdentifier("Parent"))
    }
    rehighlight(parentEditor)
    assertSize(2, getProblems(parentEditor))

    // select child editor, remove parent from a child extends list, check that the number of problems changed
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

  fun testSplitChooserSameSideTwice() {
    val classA = myFixture.addClass("public class A {}")
    val editors = manager!!.openFile(classA.containingFile.virtualFile, true)
    val editor = (UsefulTestCase.assertOneElement(editors) as TextEditorImpl).editor
    EditorTestUtil.executeAction(editor, "SplitChooser", true)
    EditorTestUtil.executeAction(editor, "SplitChooser.SplitLeft", true)
    EditorTestUtil.executeAction(editor, "SplitChooser.SplitLeft", true)
  }
}
