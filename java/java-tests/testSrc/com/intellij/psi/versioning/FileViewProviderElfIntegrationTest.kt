// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.versioning

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.elf.Elf
import com.intellij.openapi.editor.elf.ElfFeatureFlag
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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

  companion object {

    var elfEnabledBefore: Boolean = false

    @BeforeAll
    @JvmStatic
    fun enableElf() {
      elfEnabledBefore = ElfFeatureFlag.isEnabled()
      ElfFeatureFlag.setEnabled(true)
    }

    @AfterAll
    @JvmStatic
    fun disableElf() {
      ElfFeatureFlag.setEnabled(elfEnabledBefore)
    }
  }

  @BeforeEach
  fun awaitIndexing() {
    IndexingTestUtil.waitUntilIndexesAreReady(project)
    runWriteAction {
      (psiFile.manager as PsiManagerEx).fileManagerEx.forceReload(psiFile.virtualFile)
    }
  }

  @Test
  fun `FileViewProvider contents does not see Elf modifications because they are not committed`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking(context = Dispatchers.UiWithModelAccess) {
    (PsiDocumentManagerBase.getInstance(project) as PsiDocumentManagerBase).disableBackgroundCommit(disposable)
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
