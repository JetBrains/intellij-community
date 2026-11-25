// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.actions.CodeInsightAction
import com.intellij.codeInsight.multiverse.isSharedSourceSupportEnabled
import com.intellij.ide.actions.CopyElementAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.actions.BaseRefactoringAction
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * A data context can take [CommonDataKeys.PROJECT] from one component and [CommonDataKeys.EDITOR] from another one.
 * The tests give an editor of [editorProjectFixture] in a context whose PROJECT is [contextProjectFixture], as a frame
 * with several projects or an editor of another project inside a tool window does.
 */
@TestApplication
@Timeout(60)
class EditorProjectDataContextTest {
  companion object {
    private val contextProjectFixture = projectFixture().withSharedSourceEnabled()
    private val editorProjectFixture = projectFixture()
  }

  @Test
  fun `an editor component gives the project of its editor`(): Unit = timeoutRunBlocking {
    val contextProject = contextProjectFixture.get()
    withEditor(editorProjectFixture.get()) { editor, _ ->
      val context = withContext(Dispatchers.EDT) {
        projectPanel(contextProject).add(editor.component, BorderLayout.CENTER)
        Utils.createAsyncDataContext(editor.contentComponent)
      }
      readAction {
        assertThat(context.getData(CommonDataKeys.EDITOR)).isSameAs(editor)
        assertThat(context.getData(CommonDataKeys.PROJECT)).isSameAs(editor.project)
      }
    }
  }

  @Test
  fun `a context with an editor of another project reports both providers`(): Unit = timeoutRunBlocking {
    val contextProject = contextProjectFixture.get()
    withEditor(editorProjectFixture.get()) { editor, _ ->
      val error = withContext(Dispatchers.EDT) {
        val editorProvider = EditorOnlyProvider(editor)
        projectPanel(contextProject).add(editorProvider, BorderLayout.CENTER)
        LoggedErrorProcessor.executeAndReturnLoggedError {
          Utils.createAsyncDataContext(editorProvider)
        }
      }
      assertThat(error.message)
        .contains(ProjectPanel::class.java.name)
        .contains(EditorOnlyProvider::class.java.name)
    }
  }

  @Test
  fun `a context with an editor of the default project reports nothing`(): Unit = timeoutRunBlocking {
    val contextProject = contextProjectFixture.get()
    withEditor(ProjectManager.getInstance().defaultProject) { editor, _ ->
      val context = withContext(Dispatchers.EDT) {
        val editorProvider = EditorOnlyProvider(editor)
        projectPanel(contextProject).add(editorProvider, BorderLayout.CENTER)
        Utils.createAsyncDataContext(editorProvider)
      }
      readAction {
        assertThat(context.getData(CommonDataKeys.PROJECT)).isSameAs(contextProject)
      }
    }
  }

  @Test
  fun `a code insight action works in the project of the editor`(): Unit = timeoutRunBlocking {
    withEditor(editorProjectFixture.get()) { editor, file ->
      val action = RecordingCodeInsightAction()
      val event = TestActionEvent.createTestEvent(action, mismatchedContext(editor))
      readAction { action.update(event) }
      assertThat(action.updatedProject).isSameAs(editor.project)
      assertThat(action.updatedFile).isSameAs(file)
      assertThat(event.presentation.isEnabled).isTrue()
    }
  }

  @Test
  fun `a code insight action is disabled for an editor of the default project`(): Unit = timeoutRunBlocking {
    withEditor(ProjectManager.getInstance().defaultProject) { editor, _ ->
      val action = RecordingCodeInsightAction()
      val event = TestActionEvent.createTestEvent(action, mismatchedContext(editor))
      readAction { action.update(event) }
      assertThat(action.updatedProject).isNull()
      assertThat(event.presentation.isEnabled).isFalse()
    }
  }

  @Test
  fun `Copy element updates with an editor of another project`(): Unit = timeoutRunBlocking {
    // The context manager of a project with shared sources asserts the project of the editor.
    assertThat(isSharedSourceSupportEnabled(contextProjectFixture.get())).isTrue()
    withEditor(editorProjectFixture.get()) { editor, _ ->
      val action = CopyElementAction()
      val event = TestActionEvent.createTestEvent(action, mismatchedContext(editor))
      readAction { action.update(event) }
      assertThat(event.presentation.isVisible).isTrue()
    }
  }

  @Test
  fun `a refactoring handler gets the project of the editor`(): Unit = timeoutRunBlocking {
    withEditor(editorProjectFixture.get()) { editor, file ->
      val handler = RecordingRefactoringHandler()
      withContext(Dispatchers.EDT) {
        BaseRefactoringAction.performRefactoringAction(contextProjectFixture.get(), mismatchedContext(editor), handler)
      }
      assertThat(handler.invokedProject).isSameAs(editor.project)
      assertThat(handler.invokedFile).isSameAs(file)
    }
  }

  @Test
  fun `an editor action updates with the project of the editor`(): Unit = timeoutRunBlocking {
    withEditor(editorProjectFixture.get()) { editor, _ ->
      val handler = RecordingEditorActionHandler()
      val action = object : EditorAction(handler) {}
      val event = TestActionEvent.createTestEvent(action, mismatchedContext(editor))
      readAction { action.update(event) }
      assertThat(handler.updatedProject).isSameAs(editor.project)
    }
  }

  /** A context with PROJECT of [contextProjectFixture] and [editor], which belongs to another project. */
  private fun mismatchedContext(editor: Editor): DataContext =
    SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, contextProjectFixture.get())
      .add(CommonDataKeys.EDITOR, editor)
      .build()

  private fun projectPanel(project: Project): JPanel = ProjectPanel(project)

  private class ProjectPanel(private val project: Project) : JPanel(BorderLayout()), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.PROJECT] = project
    }
  }

  /** Writes [editor] and no project, as a component that holds an editor of another project can do. */
  private class EditorOnlyProvider(private val editor: Editor) : JComponent(), UiDataProvider {
    override fun uiDataSnapshot(sink: DataSink) {
      sink[CommonDataKeys.EDITOR] = editor
    }
  }

  private class RecordingCodeInsightAction : CodeInsightAction() {
    @Volatile
    var updatedProject: Project? = null

    @Volatile
    var updatedFile: PsiFile? = null

    override fun getHandler(): CodeInsightActionHandler = CodeInsightActionHandler { _, _, _ -> }

    override fun update(presentation: Presentation, project: Project, editor: Editor, psiFile: PsiFile) {
      updatedProject = project
      updatedFile = psiFile
      presentation.isEnabled = true
    }
  }

  private class RecordingRefactoringHandler : RefactoringActionHandler {
    var invokedProject: Project? = null
    var invokedFile: PsiFile? = null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?, dataContext: DataContext?) {
      invokedProject = project
      invokedFile = file
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) {
      invokedProject = project
    }
  }

  private class RecordingEditorActionHandler : EditorActionHandler() {
    @Volatile
    var updatedProject: Project? = null

    override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext?): Boolean {
      updatedProject = dataContext?.getData(CommonDataKeys.PROJECT)
      return true
    }
  }

  private suspend inline fun withEditor(project: Project, crossinline action: suspend CoroutineScope.(Editor, PsiFile) -> Unit) {
    val (editor, file) = withContext(Dispatchers.EDT) {
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
