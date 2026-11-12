// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.build.events.MessageEvent
import com.intellij.build.progress.BuildProgressDescriptorImpl
import com.intellij.build.progress.BuildRootProgressImpl
import com.intellij.mock.Mock
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProviderBean
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.ThreeState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import javax.swing.JLabel
import kotlin.io.path.writeText

/**
 * The build view opens the file of the first error by itself.
 * The EDT must not wait there until the editor loads.
 */
@TestApplication
class BuildFirstErrorNavigationTest {
  private val projectFixture = projectFixture(openAfterCreation = true)
  private val managerFixture = projectFixture.fileEditorManagerFixture()
  private val tempDirectory = tempPathFixture()

  private val buildDescriptor = DefaultBuildDescriptor(Any(), "test descriptor", "fake path", 1L)
    .withNavigateToError(ThreeState.YES)

  private val treeConsoleView by testFixture {
    val project = projectFixture.init()
    managerFixture.init()
    val view = withContext(Dispatchers.EDT) {
      BuildTreeConsoleView(project, buildDescriptor, BuildTextConsoleView(project, true, emptyList()), object : BuildViewSettingsProvider {
        override fun isExecutionViewHidden(): Boolean = false
      })
    }
    initialized(view) {
      Disposer.dispose(view)
    }
  }

  @AfterEach
  fun closeEditors(): Unit = timeoutRunBlocking {
    // the removal of the gated provider closes its editors off the EDT, so the test closes them first
    withContext(Dispatchers.UiWithModelAccess) {
      managerFixture.get().closeAllFiles()
    }
  }

  @Test
  fun `the first error opens its file without blocking EDT`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val file = createFile("Broken.txt")
    checkOpensWithoutBlockingEdt(file, disposable) {
      reportError(file, line = 1, column = 0)
    }
  }

  @Test
  fun `the first error with a file descriptor opens its file without blocking EDT`(
    @TestDisposable disposable: Disposable,
  ): Unit = timeoutRunBlocking {
    val file = createFile("Broken.txt")
    val descriptor = fileDescriptor(file, line = 1, column = 0)
    checkOpensWithoutBlockingEdt(file, disposable) {
      reportError(descriptor)
    }
  }

  @Test
  fun `the first error with a file descriptor puts the caret at its position`(): Unit = timeoutRunBlocking {
    val file = createFile("Broken.txt")

    reportError(fileDescriptor(file, line = 1, column = 3))

    awaitCaret(file, LogicalPosition(1, 3))
  }

  private suspend fun checkOpensWithoutBlockingEdt(file: VirtualFile, disposable: Disposable, reportError: () -> Unit) {
    val started = CompletableDeferred<Unit>()
    val proceed = CompletableDeferred<Unit>()
    registerGatedEditorProvider(file, started, proceed, disposable)

    try {
      reportError()
      started.await()
      withContext(Dispatchers.EDT) {
        // a blocking open runs this event inside its wait, so the wait is in the stack then
        assertThat(Thread.currentThread().stackTrace.map { it.methodName }).doesNotContain("blockingWaitForCompositeFileOpen")
      }
      proceed.complete(Unit)
      waitUntil("The file of the first error must open") {
        withContext(Dispatchers.EDT) { managerFixture.get().isFileOpen(file) }
      }
    }
    finally {
      proceed.complete(Unit)
    }
  }

  @Test
  fun `the first error puts the caret at its position`(): Unit = timeoutRunBlocking {
    val file = createFile("Broken.txt")

    reportError(file, line = 1, column = 3)

    awaitCaret(file, LogicalPosition(1, 3))
  }

  private suspend fun awaitCaret(file: VirtualFile, position: LogicalPosition) {
    waitUntil("The caret must go to the position of the first error") {
      withContext(Dispatchers.EDT) {
        val manager = managerFixture.get()
        manager.selectedFiles.toList() == listOf(file) && manager.selectedTextEditor?.caretModel?.logicalPosition == position
      }
    }
  }

  /** A build issue of a build tool gives such a descriptor as its navigatable. */
  private suspend fun fileDescriptor(file: VirtualFile, line: Int, column: Int): OpenFileDescriptor {
    return readAction { OpenFileDescriptor(projectFixture.get(), file, line, column) }
  }

  private fun reportError(navigatable: Navigatable) {
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
      .message("error", "detailed error", MessageEvent.Kind.ERROR, navigatable)
  }

  private fun reportError(file: VirtualFile, line: Int, column: Int) {
    BuildRootProgressImpl(treeConsoleView)
      .start("build started", BuildProgressDescriptorImpl(buildDescriptor))
      .fileMessage("error", "detailed error", MessageEvent.Kind.ERROR, FilePosition(file.toNioPath(), line, column))
  }

  private fun createFile(name: String): VirtualFile {
    val path = tempDirectory.get().resolve(name).apply { writeText("first line\nsecond line\n") }
    return checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path))
  }

  /**
   * Opens a tab for [target] at once, but its editor appears only after [proceed] completes.
   */
  private fun registerGatedEditorProvider(
    target: VirtualFile,
    started: CompletableDeferred<Unit>,
    proceed: CompletableDeferred<Unit>,
    disposable: Disposable,
  ) {
    FileEditorProviderBean.EP_NAME.point.registerExtension(FileEditorProviderBean(object : AsyncFileEditorProvider {
      override fun accept(project: Project, candidate: VirtualFile): Boolean = candidate == target
      override fun acceptRequiresReadAction(): Boolean = false
      override fun getEditorTypeId(): String = "build-error-gated"
      override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
      override fun createEditor(project: Project, file: VirtualFile): FileEditor = error("Use asynchronous creation")

      override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope,
      ): FileEditor {
        started.complete(Unit)
        proceed.await()
        return withContext(Dispatchers.EDT) {
          object : Mock.MyFileEditor() {
            private val component = JLabel()
            override fun getComponent(): JComponent = component
            override fun getFile(): VirtualFile = file
          }
        }
      }
    }), disposable)
  }
}
