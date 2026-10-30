// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.welcomeFiles

import com.intellij.ide.actions.WelcomeFilesRootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.vfs.rpcId
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Checks the language of a Home file that [WelcomeFilesApi] gives to the status bar widget.
 */
@TestApplication
internal class WelcomeFileLanguageTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val projectFixture = projectFixture(openAfterCreation = true)

  private val project: Project
    get() = projectFixture.get()

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
  fun `language change sends the new file type`(): Unit = timeoutRunBlocking {
    markAsWelcomeProject(project, disposable)
    val file = createWelcomeFile()
    val api = WelcomeFilesApi.getInstance()
    val languages = Channel<String>(Channel.UNLIMITED)
    val job = launch {
      api.fileLanguage(project.projectId(), file.rpcId()).collect { languages.send(it.displayName) }
    }

    assertEquals(FileTypeManager.getInstance().getFileTypeByExtension("txt").displayName, languages.receive())
    // A file type without a language gives the same result with and without the language plugins.
    val item = api.getLanguages().first { Language.findLanguageByID(it.id) == null }
    api.changeLanguage(project.projectId(), file.rpcId(), item.id)
    val fileType = FileTypeManager.getInstance().getFileTypeByExtension(item.extension)
    while (languages.receive() != fileType.displayName) {
      // The flow can send the old language once more before the file gets the new extension.
    }
    job.cancel()
  }

  @Test
  fun `file outside the welcome project has no language`(): Unit = timeoutRunBlocking {
    val file = createWelcomeFile()

    assertNull(WelcomeFilesApi.getInstance().fileLanguage(project.projectId(), file.rpcId()).firstOrNull())
  }

  private suspend fun createWelcomeFile(): VirtualFile {
    val file = edtWriteAction {
      WelcomeFilesRootType.Util.instance.findFile(project, "Untitled.txt", ScratchFileService.Option.create_new_always)
    }
    createdFiles.add(file)
    return file
  }
}
