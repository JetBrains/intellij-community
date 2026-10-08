// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles

import com.intellij.ide.actions.WelcomeFilesRootType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.fileChooser.impl.FileChooserFactoryImpl
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.tabActions.ALWAYS_SHOW_MODIFIED_MARKER
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.platform.project.projectId
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingConstants
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the Home files of the welcome project: the modified marker on the tab, and the prompts when a tab or the project closes.
 * A test save dialog replaces the real one. It selects no target, so the tests check no copy of a file.
 * A test directory chooser replaces the real one for the save of several files on the project close.
 */
@TestApplication
internal class WelcomeFilesCloseTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val projectFixture = projectFixture(
    openProjectTask = OpenProjectTask {
      beforeInitTasks += { it.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true) }
    },
    openAfterCreation = true,
  )
  private val fileEditorManagerFixture = projectFixture.fileEditorManagerFixture()
  private val tempDirFixture = tempPathFixture()

  private val project: Project
    get() = projectFixture.get()

  private val manager: FileEditorManagerImpl
    get() = fileEditorManagerFixture.get()

  private val createdFiles = ArrayList<VirtualFile>()

  @AfterEach
  fun deleteCreatedFiles(): Unit = timeoutRunBlocking {
    edtWriteAction {
      for (file in createdFiles) {
        if (file.isValid) {
          file.delete(this)
        }
      }
    }
  }

  @Test
  fun `cancel keeps the tab and the file`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project)
    val file = createWelcomeFile()
    openAndAwaitMarker(file)
    TestDialogManager.setTestDialog({ Messages.CANCEL }, disposable)

    val closed = withContext(Dispatchers.UiWithModelAccess) {
      manager.closeFileWithChecks(file, manager.currentWindow!!)
    }

    assertFalse(closed)
    assertTrue(withContext(Dispatchers.UiWithModelAccess) { manager.isFileOpen(file) })
    assertTrue(file.isValid)
  }

  @Test
  fun `do not save closes the tab and deletes the file`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project)
    val file = createWelcomeFile()
    openAndAwaitMarker(file)
    TestDialogManager.setTestDialog(TestDialog.NO, disposable)

    val closed = withContext(Dispatchers.UiWithModelAccess) {
      manager.closeFileWithChecks(file, manager.currentWindow!!)
    }

    assertTrue(closed)
    assertFalse(withContext(Dispatchers.UiWithModelAccess) { manager.isFileOpen(file) })
    waitUntil("The Home file is not deleted", timeout = 5.seconds) { !file.isValid }
  }

  @Test
  fun `close all stops the questions at the first cancel`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project)
    val discardedFile = createWelcomeFile()
    val cancelledFile = createWelcomeFile()
    val notAskedFile = createWelcomeFile()
    for (file in listOf(discardedFile, cancelledFile, notAskedFile)) {
      openAndAwaitMarker(file)
    }
    val regularFile = LightVirtualFile("regular.txt", "text")
    withContext(Dispatchers.UiWithModelAccess) {
      manager.openFile(regularFile, true)
    }
    val answers = listOf(Messages.NO, Messages.CANCEL)
    var questionCount = 0
    TestDialogManager.setTestDialog({ answers[questionCount++] }, disposable)

    val closedAll = withContext(Dispatchers.UiWithModelAccess) {
      val window = manager.currentWindow!!
      val filesToClose = listOf(discardedFile, cancelledFile, notAskedFile, regularFile)
      manager.closeFilesWithChecks(filesToClose.map { Pair.create(window.getComposite(it)!!, window) })
    }

    assertFalse(closedAll)
    assertEquals(2, questionCount)
    withContext(Dispatchers.UiWithModelAccess) {
      assertFalse(manager.isFileOpen(discardedFile))
      assertTrue(manager.isFileOpen(cancelledFile))
      assertTrue(manager.isFileOpen(notAskedFile))
      assertFalse(manager.isFileOpen(regularFile))
    }
    waitUntil("The Home file is not deleted", timeout = 5.seconds) { !discardedFile.isValid }
    assertTrue(cancelledFile.isValid)
    assertTrue(notAskedFile.isValid)
  }

  @Test
  fun `a second close during the save asks nothing`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project)
    val file = createWelcomeFile()
    openAndAwaitMarker(file)
    val answers = listOf(Messages.OK, Messages.CANCEL)
    var questionCount = 0
    TestDialogManager.setTestDialog({ answers[questionCount++] }, disposable)
    val closedDuringSave = CompletableDeferred<Boolean>()
    replaceSaveDialog {
      closedDuringSave.complete(manager.closeFileWithChecks(file, manager.currentWindow!!))
    }

    val closed = withContext(Dispatchers.UiWithModelAccess) {
      manager.closeFileWithChecks(file, manager.currentWindow!!)
    }

    assertFalse(closed)
    assertFalse(closedDuringSave.await())
    assertEquals(1, questionCount)
    // The save dialog selects no target, so a close after the dialog asks again.
    waitUntil("The close after the save dialog does not ask", timeout = 5.seconds) {
      withContext(Dispatchers.UiWithModelAccess) {
        manager.closeFileWithChecks(file, manager.currentWindow!!)
        questionCount == 2
      }
    }
    assertTrue(withContext(Dispatchers.UiWithModelAccess) { manager.isFileOpen(file) })
  }

  @Test
  fun `a failed save does not stop the next saves`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project)
    val failedFile = createWelcomeFile()
    val nextFile = createWelcomeFile()
    for (file in listOf(failedFile, nextFile)) {
      openAndAwaitMarker(file)
    }
    TestDialogManager.setTestDialog(TestDialog.OK, disposable)
    val savedFileNames = ArrayList<String>()
    replaceSaveDialog { fileName ->
      savedFileNames += fileName
      check(fileName != failedFile.name) { "The test save dialog fails" }
    }

    val loggedErrors = CopyOnWriteArrayList<String>()
    val errorProcessor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        loggedErrors += message
        return Action.NONE
      }
    }

    LoggedErrorProcessor.executeWith(errorProcessor).use {
      withContext(Dispatchers.UiWithModelAccess) {
        val window = manager.currentWindow!!
        manager.closeFilesWithChecks(listOf(failedFile, nextFile).map { Pair.create(window.getComposite(it)!!, window) })
      }

      waitUntil("The save of the next file does not start", timeout = 5.seconds) {
        withContext(Dispatchers.UiWithModelAccess) { savedFileNames.size == 2 }
      }
    }

    assertEquals(listOf(failedFile.name, nextFile.name), savedFileNames)
    assertEquals(listOf("Cannot save the Home file ${failedFile.name}"), loggedErrors)
  }

  @Test
  fun `a split copy closes without a question`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project)
    val file = createWelcomeFile()
    openAndAwaitMarker(file)
    var questionCount = 0
    TestDialogManager.setTestDialog({ questionCount++; Messages.NO }, disposable)
    val (firstWindow, secondWindow) = withContext(Dispatchers.UiWithModelAccess) {
      val window = manager.currentWindow!!
      window to window.split(SwingConstants.VERTICAL, true, file, true)!!
    }
    waitUntil("The file is not open in two windows", timeout = 5.seconds) {
      withContext(Dispatchers.UiWithModelAccess) { manager.splitters.getAllComposites(file).size == 2 }
    }

    val closedCopy = withContext(Dispatchers.UiWithModelAccess) {
      manager.closeFileWithChecks(file, secondWindow)
    }

    assertTrue(closedCopy)
    assertEquals(0, questionCount)
    assertTrue(withContext(Dispatchers.UiWithModelAccess) { manager.isFileOpen(file) })
    assertTrue(file.isValid)

    val closedLast = withContext(Dispatchers.UiWithModelAccess) {
      manager.closeFileWithChecks(file, firstWindow)
    }

    assertTrue(closedLast)
    assertEquals(1, questionCount)
    waitUntil("The Home file is not deleted", timeout = 5.seconds) { !file.isValid }
  }

  @Test
  fun `backend finds a Home file only in the welcome project`(): Unit = timeoutRunBlocking {
    val file = createWelcomeFile()
    val api = WelcomeFilesApi.getInstance()
    assertFalse(api.isWelcomeFile(project.projectId(), file.rpcId()))

    markAsWelcomeProject(project)
    assertTrue(api.isWelcomeFile(project.projectId(), file.rpcId()))
  }

  @Test
  fun `project close cancel keeps the file`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project)
    val file = createWelcomeFile()
    openAndAwaitMarker(file)
    var questionCount = 0
    TestDialogManager.setTestDialog({ questionCount++; Messages.CANCEL }, disposable)

    // The project manager calls the close handlers under the write-intent lock, so the test does the same.
    val canClose = withContext(Dispatchers.EDT) {
      ProjectManagerEx.getInstanceEx().canClose(project)
    }

    assertFalse(canClose)
    assertEquals(1, questionCount)
    assertTrue(withContext(Dispatchers.UiWithModelAccess) { manager.isFileOpen(file) })
    assertTrue(file.isValid)
  }

  @Test
  fun `project close with do not save deletes the files after one question`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project)
    val files = listOf(createWelcomeFile(), createWelcomeFile())
    for (file in files) {
      openAndAwaitMarker(file)
    }
    var questionCount = 0
    TestDialogManager.setTestDialog({ questionCount++; Messages.NO }, disposable)

    // The project manager calls the close handlers under the write-intent lock, so the test does the same.
    val canClose = withContext(Dispatchers.EDT) {
      ProjectManagerEx.getInstanceEx().canClose(project)
    }

    assertTrue(canClose)
    assertEquals(1, questionCount)
    withContext(Dispatchers.UiWithModelAccess) {
      for (file in files) {
        assertFalse(manager.isFileOpen(file))
      }
    }
    waitUntil("The Home files are not deleted", timeout = 5.seconds) { files.none { it.isValid } }
  }

  @Test
  fun `project close with save copies the files to the selected directory`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project)
    val files = listOf(createWelcomeFile(), createWelcomeFile())
    for (file in files) {
      openAndAwaitMarker(file)
    }
    val fileNames = files.map { it.name }.sorted()
    TestDialogManager.setTestDialog(TestDialog.OK, disposable)
    val targetDir = withContext(Dispatchers.IO) {
      LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDirFixture.get())!!
    }
    replacePathChooser(targetDir)

    // The project manager calls the close handlers under the write-intent lock, so the test does the same.
    val canClose = withContext(Dispatchers.EDT) {
      ProjectManagerEx.getInstanceEx().canClose(project)
    }

    assertTrue(canClose)
    waitUntil("The Home files are not deleted", timeout = 5.seconds) { files.none { it.isValid } }
    assertEquals(fileNames, targetDir.children.map { it.name }.sorted())
  }

  @Test
  fun `project close with save of one file uses the save dialog`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project)
    val file = createWelcomeFile()
    openAndAwaitMarker(file)
    TestDialogManager.setTestDialog(TestDialog.OK, disposable)
    val savedFileNames = ArrayList<String>()
    replaceSaveDialog { savedFileNames += it }

    // The project manager calls the close handlers under the write-intent lock, so the test does the same.
    val canClose = withContext(Dispatchers.EDT) {
      ProjectManagerEx.getInstanceEx().canClose(project)
    }

    // The test save dialog selects no target, so the close stops and the file stays.
    assertFalse(canClose)
    assertEquals(listOf(file.name), savedFileNames)
    assertTrue(file.isValid)
  }

  private suspend fun createWelcomeFile(): VirtualFile {
    val file = edtWriteAction {
      WelcomeFilesRootType.Util.instance.findFile(project, "Untitled.txt", ScratchFileService.Option.create_new_always)
    }
    createdFiles.add(file)
    return file
  }

  private suspend fun openAndAwaitMarker(file: VirtualFile) {
    withContext(Dispatchers.UiWithModelAccess) {
      manager.openFile(file, true)
    }
    waitUntil("The Home file tab has no modified marker", timeout = 5.seconds) {
      file.getUserData(ALWAYS_SHOW_MODIFIED_MARKER) == true
    }
  }

  /**
   * Replaces the save dialog with a dialog that calls [onShow] with the file name and selects no target.
   */
  private fun replaceSaveDialog(onShow: (String) -> Unit) {
    val factory = object : FileChooserFactoryImpl() {
      override fun createSaveFileDialog(descriptor: FileSaverDescriptor, project: Project?): FileSaverDialog {
        return object : FileSaverDialog {
          override fun save(baseDir: VirtualFile?, filename: String?): VirtualFileWrapper? {
            onShow(filename.orEmpty())
            return null
          }

          override fun save(baseDir: Path?, filename: String?): VirtualFileWrapper? {
            onShow(filename.orEmpty())
            return null
          }
        }
      }
    }
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, disposable)
  }

  /**
   * Replaces the directory chooser with a chooser that selects [targetDir].
   */
  private fun replacePathChooser(targetDir: VirtualFile) {
    val factory = object : FileChooserFactoryImpl() {
      override fun createPathChooser(descriptor: FileChooserDescriptor, project: Project?, parent: Component?): PathChooserDialog {
        return PathChooserDialog { _, callback -> callback.consume(listOf(targetDir)) }
      }
    }
    ApplicationManager.getApplication().replaceService(FileChooserFactory::class.java, factory, disposable)
  }

  // A fresh mask replaces the provider list, so the service drops the capabilities it cached for the project.
  private fun markAsWelcomeProject(welcomeProject: Project) {
    val provider = object : ProjectFrameCapabilitiesProvider {
      override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
        return if (project === welcomeProject) setOf(ProjectFrameCapability.WELCOME_EXPERIENCE) else emptySet()
      }

      override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? = null
    }
    ExtensionTestUtil.maskExtensions(ProjectFrameCapabilitiesService.EP_NAME, listOf(provider), disposable)
  }
}
