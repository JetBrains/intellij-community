// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiDocumentManagerEx
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubIndexImpl
import com.intellij.psi.stubs.StubTreeBuilder
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.FileContentImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@TestApplication
internal class ChangedFilesCollectorReentrancyTest {
  private val project = projectFixture(openAfterCreation = true)
  private val module = project.moduleFixture("src")
  private val sourceRoot = module.sourceRootFixture()
  private val file = sourceRoot.virtualFileFixture("Reentrant.java", "class Reentrant { int field; }")

  /** Reproduces IJPL-258314 with an incompatible element type in a loaded Java stub tree. */
  @Test
  fun `corrupt stubs do not deadlock the precise check`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val virtualFile = file.get()
    val project = project.get()
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    assertThat(StubIndexImpl.PER_FILE_ELEMENT_TYPE_STUB_CHANGE_TRACKING_SOURCE)
      .isEqualTo(StubIndexImpl.PerFileElementTypeStubChangeTrackingSource.ChangedFilesCollector)

    val documentManager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerEx
    documentManager.disableBackgroundCommit(disposable)
    val psi = readAction { PsiManager.getInstance(project).findFile(virtualFile) as PsiFileImpl }
    val document = readAction { FileDocumentManager.getInstance().getDocument(virtualFile)!! }
    val stubTree = readAction {
      assertThat(psi.isContentsLoaded).isFalse()
      assertThat(psi.stubTree).isNotNull()
      psi.stubTree!!
    }
    val incompatibleStubs = readAction {
      val content = FileContentImpl.createByContent(virtualFile, "class Reentrant { void method() {} }".toByteArray())
      (content as FileContentImpl).project = project
      (StubTreeBuilder.buildStubTree(content) as StubBase<*>).stubList
    }
    edtWriteAction {
      CommandProcessor.getInstance().runUndoTransparentAction {
        document.insertString(document.textLength, " ")
      }
    }

    readAction {
      assertThat(documentManager.isUncommited(document)).isTrue()
      assertThat(psi.isContentsLoaded).isFalse()
      assertThat(psi.derefStub()).isEqualTo(stubTree)
      val stubs = stubTree.plainList
      assertThat(stubs).hasSizeGreaterThan(1)
      stubs.forEach { it.psi }
      val incompatibleIndex = stubs.indices.first {
        it > 0 && it < incompatibleStubs.size && stubs[it].elementType != incompatibleStubs[it].elementType
      }
      // Bind one stub to a list with a different element type at the same position,
      // simulating stub inconsistency
      (stubs[incompatibleIndex] as StubBase<*>).stubList = incompatibleStubs
    }

    val collector = (FileBasedIndex.getInstance() as FileBasedIndexImpl).changedFilesCollector
    collector.eventMerger.recordTransientStateChangeEvent(virtualFile)
    // The diagnostics run in a non-cancelable section. Interrupt the Phaser wait when the test times out.
    runInterruptible(Dispatchers.IO) {
      runReadActionBlocking {
        assertThatThrownBy { collector.ensureUpToDate() }
          .isInstanceOf(RuntimeExceptionWithAttachments::class.java)
          .hasMessageContaining("PSI and index do not match")
      }
    }
  }
}
