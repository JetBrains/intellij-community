// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.common.fakeLspIntegrationFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.utils.vfs.getDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.eclipse.lsp4j.FileOperationFilter
import org.eclipse.lsp4j.FileOperationOptions
import org.eclipse.lsp4j.FileOperationPattern
import org.eclipse.lsp4j.FileOperationPatternKind
import org.eclipse.lsp4j.FileOperationPatternOptions
import org.eclipse.lsp4j.FileOperationsServerCapabilities
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.Registration
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.Unregistration
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceServerCapabilities
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

@TestApplication
internal class LspWillRenameFilesTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val project by projectFixture
  }

  private val codeInsightFixture by codeInsightModuleFixture(projectFixture)

  @Nested
  inner class StaticRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(configureServerCapabilities = {
      this.workspace = WorkspaceServerCapabilities().apply {
        this.fileOperations = FileOperationsServerCapabilities().apply {
          this.willRename = FileOperationOptions(listOf(
            FileOperationFilter(FileOperationPattern("**/*.txt").apply { matches = FileOperationPatternKind.File }, "file"),
            FileOperationFilter(FileOperationPattern("**/dir*").apply { matches = FileOperationPatternKind.Folder }, null),
            FileOperationFilter(FileOperationPattern("**/*.CASE").apply { options = FileOperationPatternOptions(true) }, null),
          ))
        }
      }
    })

    private val otherDirPath by tempPathFixture()

    @Test
    fun `rename applies the edit before the file is renamed`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val oldName = file.name
      val other = codeInsightFixture.addFileToProject("other.txt", "import $oldName").virtualFile
      val session = fakeLspIntegration.serverSession(file)
      val oldUri = session.fileUri(file)
      val otherUri = session.fileUri(other)
      val nameOnRequest = AtomicReference<String>()
      val request = AtomicReference<RenameFilesParams>()

      session.expectRequest(session.WILL_RENAME_FILES) { params ->
        request.set(params)
        nameOnRequest.set(file.name)
        WorkspaceEdit(mapOf(otherUri to listOf(TextEdit(Range(Position(0, 7), Position(0, 7 + oldName.length)), "new.txt"))))
      }

      rename(file, "new.txt")

      session.awaitExpected()
      assertEquals(listOf(FileRename(oldUri, oldUri.removeSuffix(oldName) + "new.txt")), request.get().files)
      assertEquals(oldName, nameOnRequest.get())
      assertEquals("new.txt", file.name)
      assertEquals("import new.txt", readAction { other.getDocument().text })
    }

    @Test
    fun `rename sends the uri of the renamed file`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val session = fakeLspIntegration.serverSession(file)
      val request = AtomicReference<RenameFilesParams>()

      session.expectRequest(session.WILL_RENAME_FILES) { params ->
        request.set(params)
        WorkspaceEdit()
      }

      rename(file, "new #1 %\u0444 (x).txt")

      session.awaitExpected()
      assertEquals(session.fileUri(file), request.get().files.single().newUri)
    }

    @Test
    fun `move sends the new uri`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val target = edtWriteAction { file.parent.createChildDirectory(null, "target") }
      val session = fakeLspIntegration.serverSession(file)
      val oldUri = session.fileUri(file)
      val targetUri = session.fileUri(target)
      val request = AtomicReference<RenameFilesParams>()

      session.expectRequest(session.WILL_RENAME_FILES) { params ->
        request.set(params)
        WorkspaceEdit()
      }

      move(listOf(file), target)

      session.awaitExpected()
      assertEquals(listOf(FileRename(oldUri, "$targetUri/${file.name}")), request.get().files)
      assertEquals(target, file.parent)
    }

    @Test
    fun `move skips the files that stay in place`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val nameTaken = codeInsightFixture.addFileToProject("taken.txt", "").virtualFile
      val target = edtWriteAction { file.parent.createChildDirectory(null, "target") }
      edtWriteAction { target.createChildData(null, "taken.txt") }
      val alreadyInTarget = edtWriteAction { target.createChildData(null, "inTarget.txt") }
      val session = fakeLspIntegration.serverSession(file)
      val oldUri = session.fileUri(file)
      val targetUri = session.fileUri(target)
      val request = AtomicReference<RenameFilesParams>()

      session.expectRequest(session.WILL_RENAME_FILES) { params ->
        request.set(params)
        WorkspaceEdit()
      }

      move(listOf(file, nameTaken, alreadyInTarget), target)

      session.awaitExpected()
      assertEquals(listOf(FileRename(oldUri, "$targetUri/old.txt")), request.get().files)
      assertEquals(target, file.parent)
    }

    @Test
    fun `directory rename sends only the directory`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val directory = edtWriteAction {
        file.parent.createChildDirectory(null, "dir").also { it.createChildData(null, "child.txt") }
      }
      val session = fakeLspIntegration.serverSession(file)
      val oldUri = session.fileUri(directory)
      val request = AtomicReference<RenameFilesParams>()

      session.expectRequest(session.WILL_RENAME_FILES) { params ->
        request.set(params)
        WorkspaceEdit()
      }

      rename(directory, "newDir")

      session.awaitExpected()
      assertEquals(listOf(FileRename(oldUri, oldUri.removeSuffix("dir") + "newDir")), request.get().files)
      assertEquals("newDir", directory.name)
    }

    @Test
    fun `filters select the files`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val markdown = codeInsightFixture.addFileToProject("notes.md", "").virtualFile
      val fileWithFolderName = codeInsightFixture.addFileToProject("dirLike", "").virtualFile
      val session = fakeLspIntegration.serverSession(file)

      val request = session.expectRequest(session.WILL_RENAME_FILES) { WorkspaceEdit() }
      rename(markdown, "newNotes.md")
      rename(fileWithFolderName, "newDirLike")

      assertFalse(request.isCompleted, "The request must not be sent")
      request.cancel()
      assertEquals("newNotes.md", markdown.name)
      assertEquals("newDirLike", fileWithFolderName.name)
    }

    @Test
    fun `ignoreCase filter matches any case`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val other = codeInsightFixture.addFileToProject("old.case", "").virtualFile
      val session = fakeLspIntegration.serverSession(file)
      val oldUri = session.fileUri(other)

      session.expectRequest(session.WILL_RENAME_FILES, { it.files.single().oldUri == oldUri }) { WorkspaceEdit() }
      rename(other, "new.case")

      session.awaitExpected()
    }

    @Test
    fun `rename continues when the server returns null`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val session = fakeLspIntegration.serverSession(file)

      session.expectRequest(session.WILL_RENAME_FILES) { null }
      rename(file, "new.txt")

      session.awaitExpected()
      assertEquals("new.txt", file.name)
    }

    @Test
    fun `rename continues when the server does not answer`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val session = fakeLspIntegration.serverSession(file)

      session.expectRequest(session.WILL_RENAME_FILES) { awaitCancellation() }
      rename(file, "new.txt")

      session.awaitExpected()
      assertEquals("new.txt", file.name)
    }

    @Test
    fun `rename continues when the server fails`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val session = fakeLspIntegration.serverSession(file)

      session.expectRequest(session.WILL_RENAME_FILES) {
        throw ResponseErrorException(ResponseError(ResponseErrorCode.InternalError, "Server failure", null))
      }
      rename(file, "new.txt")

      session.awaitExpected()
      assertEquals("new.txt", file.name)
    }

    @Test
    fun `file outside the server roots gets no request`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val session = fakeLspIntegration.serverSession(file)
      val otherDir = edtWriteAction { VfsUtil.createDirectories(otherDirPath.toString()) }
      val outsideFile = edtWriteAction { otherDir.createChildData(null, "outside.txt") }

      val request = session.expectRequest(session.WILL_RENAME_FILES) { WorkspaceEdit() }
      rename(outsideFile, "renamed.txt")

      assertFalse(request.isCompleted, "The request must not be sent")
      request.cancel()
      assertEquals("renamed.txt", outsideFile.name)
    }
  }

  @Nested
  inner class DynamicRegistration {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture()

    @Test
    fun `dynamic registration enables the request`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val session = fakeLspIntegration.serverSession(file)
      val oldUri = session.fileUri(file)

      session.sendRequest(session.REGISTER_CAPABILITY) {
        val options = FileOperationOptions(listOf(FileOperationFilter(FileOperationPattern("**/*.txt"), null)))
        RegistrationParams(listOf(Registration("will-rename", "workspace/willRenameFiles", options)))
      }
      session.expectRequest(session.WILL_RENAME_FILES, { it.files.single().oldUri == oldUri }) { WorkspaceEdit() }
      rename(file, "new.txt")
      session.awaitExpected()

      session.sendRequest(session.UNREGISTER_CAPABILITY) {
        UnregistrationParams(listOf(Unregistration("will-rename", "workspace/willRenameFiles")))
      }
      val request = session.expectRequest(session.WILL_RENAME_FILES) { WorkspaceEdit() }
      rename(file, "newer.txt")

      assertFalse(request.isCompleted, "The request must not be sent")
      request.cancel()
      assertEquals("newer.txt", file.name)
    }
  }

  @Nested
  inner class ClientCapabilityDisabled {
    private val fakeLspIntegration by projectFixture.fakeLspIntegrationFixture(
      configureClientCapabilities = {
        this.workspace?.fileOperations?.willRename = false
      },
      configureServerCapabilities = {
        this.workspace = WorkspaceServerCapabilities().apply {
          this.fileOperations = FileOperationsServerCapabilities().apply {
            this.willRename = FileOperationOptions(listOf(FileOperationFilter(FileOperationPattern("**/*.txt"), null)))
          }
        }
      },
    )

    @Test
    fun `disabled willRename client capability sends no request`() = timeoutRunBlocking {
      val file = codeInsightFixture.configureByText("old.txt", "").virtualFile
      val session = fakeLspIntegration.serverSession(file)

      val request = session.expectRequest(session.WILL_RENAME_FILES) { WorkspaceEdit() }
      rename(file, "new.txt")

      assertFalse(request.isCompleted, "The request must not be sent")
      request.cancel()
      assertEquals("new.txt", file.name)
    }
  }

  private suspend fun rename(fileOrDir: VirtualFile, newName: String) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val psiManager = PsiManager.getInstance(project)
        val fileSystemItem =
          if (fileOrDir.isDirectory) psiManager.findDirectory(fileOrDir)
          else psiManager.findFile(fileOrDir)

        RenameProcessor(project, fileSystemItem!!, newName, false, false)
          .run()
      }
    }
  }

  private suspend fun move(filesOrDirs: List<VirtualFile>, targetDir: VirtualFile) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        val psiManager = PsiManager.getInstance(project)
        val elements = filesOrDirs.map {
          if (it.isDirectory) psiManager.findDirectory(it)!!
          else psiManager.findFile(it)!!
        }
        MoveFilesOrDirectoriesProcessor(project, elements.toTypedArray(), psiManager.findDirectory(targetDir)!!, false, false, null, null)
          .run()
      }
    }
  }
}
