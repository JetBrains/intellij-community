// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles

import com.intellij.ide.actions.WelcomeFilesRootType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.tabActions.ALWAYS_SHOW_MODIFIED_MARKER
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.platform.project.projectId
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.fileEditorManagerFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the Home files of the welcome project: the modified marker on the tab, and the prompt when the tab closes.
 * The "Save" answer is not checked, because it needs a file chooser.
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
  fun `backend finds a Home file only in the welcome project`(): Unit = timeoutRunBlocking {
    val file = createWelcomeFile()
    val api = WelcomeFilesApi.getInstance()
    assertFalse(api.isWelcomeFile(project.projectId(), file.rpcId()))

    markAsWelcomeProject(project)
    assertTrue(api.isWelcomeFile(project.projectId(), file.rpcId()))
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
