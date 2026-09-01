// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.versioning

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.elf.Elf
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.editorFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestApplication
internal class FileViewProviderElfIntegrationTest {

  private val _tempDir = tempPathFixture()
  private val _project = projectFixture(_tempDir, openAfterCreation = true)
  private val _module = _project.moduleFixture("basic")
  private val _sourceRoot = _module.sourceRootFixture(pathFixture = _tempDir)
  private val _psiFile = _sourceRoot.psiFileFixture("Main.java", """
    public class Main {
      public static void main(String[] args) {
         <caret> 
      }
    }
  """.trimIndent())
  private val _editor = _psiFile.editorFixture()

  private val project by _project
  private val editor by _editor
  private val psiFile by _psiFile

  @BeforeEach
  fun awaitIndexing() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    runWriteAction {
      (psiFile.manager as PsiManagerEx).fileManagerEx.forceReload(psiFile.virtualFile)
    }
  }

  /** AI-generated test. */
  @Test
  fun `psiFile can be created and parsed in versioned environment`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val offset = readAction {
      editor.caretModel.offset
    }
    (PsiDocumentManagerBase.getInstance(project) as PsiDocumentManagerBase).disableBackgroundCommit(disposable)
    withContext(Dispatchers.UiWithModelAccess) {
      PsiDocumentManager.getInstance(project).allowIsolatedCommits(editor.document) {
        Elf.getElf().withElfScope {
          val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
          assertNotNull(file, "PsiFile can be created in versioned environment")
          val element = file.findElementAt(offset)
          assertNotNull(element, "PSI tree can be parsed in versioned environment")

          CommandProcessor.getInstance().executeCommand(project, {
            editor.document.insertString(offset, "System.out.println(\"Hello World!\");")
          }, "kek", null)

          assertFalse(file.text.contains("Hello World!"), "FileViewProvider must not yet contain the modified text")
          assertFalse(file.node.text.contains("Hello World!"), "Nodes must not yet contain the modified text")

          PsiDocumentManager.getInstance(project).commitDocument(editor.document)

          assertTrue(file.text.contains("System.out.println(\"Hello World!\")"), "FileViewProvider must refer to the committed text")
          assertTrue(file.node.text.contains("System.out.println(\"Hello World!\")"), "PsiFile nodes must contain committed text")
        }
      }

      PsiVersioningService.freezePsiVersion {
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
        assertFalse(file.text.contains("System.out.println(\"Hello World!\")"), "Exclusive changes are not visible in FileViewProvider")
        assertFalse(file.node.text.contains("System.out.println(\"Hello World!\")"), "Exclusive changes are not visible in ASTNodes")
      }

      PsiDocumentManager.getInstance(project).allowIsolatedCommits(editor.document) {
        val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
        assertTrue(file.text.contains("System.out.println(\"Hello World!\")"), "FileViewProvider must refer to the committed text")
        assertTrue(file.node.text.contains("System.out.println(\"Hello World!\")"), "PsiFile nodes must contain committed text")
      }
    }
  }

  @Test
  fun `FileViewProvider contents does not see Elf modifications because they are not committed`(): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    val initialText = "initial"
    val modifiedText = "modified in elf"

    runWriteAction {
      editor.document.setText(initialText)
    }

    PsiVersioningService.freezePsiVersion {
      val fileWithoutTree = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
      assertTrue { fileWithoutTree.viewProvider.isPhysical }
      fileWithoutTree.assertAstState(shouldBeBuilt = false)
      assertEquals(initialText, fileWithoutTree.viewProvider.contents.toString())
      Elf.getElf().withElfScope {
        editor.document.setText(modifiedText)
        fileWithoutTree.assertAstState(shouldBeBuilt = false)
        assertEquals(initialText, fileWithoutTree.viewProvider.contents.toString())
      }
      assertEquals(initialText, fileWithoutTree.viewProvider.contents.toString())
    }

    Elf.getElf().withElfScope {
      val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
      assertEquals(modifiedText, editor.document.text)
      // todo: this test just fixes the existing behavior. Tecnhically, the modified ELF document is not committed, and we need to assert equality with the initial text.
      //    this will be done when the lightweight commit arrives
      assertEquals(initialText, file.viewProvider.contents.toString())
    }
  }

}
