// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.listeners.RefactoringEventData
import com.intellij.refactoring.listeners.RefactoringEventListener
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The before data of file move and rename refactorings carries the target, so a [RefactoringEventListener.refactoringStarted]
 * listener knows the old and the new paths before the files change.
 */
@TestApplication
internal class FileRefactoringEventDataTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val moduleFixture = projectFixture.moduleFixture()
  }

  private val rootFixture = moduleFixture.sourceRootFixture()

  private val project get() = projectFixture.get()
  private val root get() = rootFixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @Test
  fun `file move carries target directory`() = doTest {
    val (from, to) = edtWriteAction { root.createSubdirectory("from") to root.createSubdirectory("to") }
    val file = edtWriteAction { from.createFile("A.txt") }

    val started = recordStarted {
      MoveFilesOrDirectoriesProcessor(project, arrayOf(file), to, false, false, false, null, null).run()
    }

    assertThat(started).containsExactly(Started(
      id = "refactoring.move",
      elements = listOf("${from.path}/A.txt"),
      target = to.path,
      renames = null,
    ))
    assertThat(file.virtualFile.path).isEqualTo("${to.path}/A.txt")
  }

  @Test
  fun `directory move carries target directory`() = doTest {
    val (from, to) = edtWriteAction { root.createSubdirectory("from") to root.createSubdirectory("to") }
    val dir = edtWriteAction { from.createSubdirectory("dir").also { it.createFile("A.txt") } }
    val oldPath = dir.path

    val started = recordStarted {
      MoveFilesOrDirectoriesProcessor(project, arrayOf(dir), to, false, false, false, null, null).run()
    }

    assertThat(started).containsExactly(Started(
      id = "refactoring.move",
      elements = listOf(oldPath),
      target = to.path,
      renames = null,
    ))
    assertThat(dir.path).isEqualTo("${to.path}/dir")
  }

  @Test
  fun `file rename carries new name`() = doTest {
    val file = edtWriteAction { root.createFile("A.txt") }
    val oldPath = file.path

    val started = recordStarted {
      RenameProcessor(project, file, "B.txt", false, false).run()
    }

    assertThat(started).containsExactly(Started(
      id = "refactoring.rename",
      elements = null,
      target = null,
      renames = mapOf(oldPath to "B.txt"),
    ))
    assertThat(file.name).isEqualTo("B.txt")
  }

  /** What a listener sees in [RefactoringEventListener.refactoringStarted], as paths taken at that moment. */
  private data class Started(val id: String, val elements: List<String>?, val target: String?, val renames: Map<String, String>?)

  private suspend fun recordStarted(refactoring: () -> Unit): List<Started> {
    val started = mutableListOf<Started>()
    val connection = project.messageBus.connect(disposable)
    connection.subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, object : RefactoringEventListener {
      override fun refactoringStarted(refactoringId: String, beforeData: RefactoringEventData?) {
        started += Started(
          id = refactoringId,
          elements = beforeData?.getUserData(RefactoringEventData.PSI_ELEMENT_ARRAY_KEY)?.map { it.path },
          target = beforeData?.getUserData(RefactoringEventData.TARGET_ELEMENT_KEY)?.path,
          renames = beforeData?.getUserData(RefactoringEventData.RENAMES_KEY)?.mapKeys { it.key.path },
        )
      }

      override fun refactoringDone(refactoringId: String, afterData: RefactoringEventData?) {}
    })
    try {
      withContext(Dispatchers.EDT) { refactoring() }
    }
    finally {
      connection.disconnect()
    }
    return started
  }

  private val PsiElement.path: String get() = (this as PsiFileSystemItem).virtualFile.path

  private fun doTest(block: suspend () -> Unit) = timeoutRunBlocking(60.seconds) {
    IndexingTestUtil.suspendUntilIndexesAreReady(project)
    block()
  }
}
