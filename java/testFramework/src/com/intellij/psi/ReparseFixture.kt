// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.text.BlockSupport
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals

/**
 * The fixture of the incremental reparse tests. Create it with [reparseFixture].
 *
 * A test creates [dummyFile] of [fileType] with [prepareFile], and changes it at the edit point with [insert] and [remove].
 * After each change, the fixture compares the tree of [dummyFile] with the tree of a new file of the same text.
 * The helpers that change or create a file run on the EDT. A test that reads the PSI of [dummyFile] needs its own read action.
 */
@TestOnly
class ReparseFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  /**
   * The name of the test method, as `getName()` of a JUnit 3 test returns it.
   */
  val name: String,
  private val dummyFileFactory: DummyFileFactory?,
) {
  /**
   * The file type of the files that [prepareFile] creates.
   */
  var fileType: FileType? = null

  /**
   * The file that [prepareFile] creates and that [insert], [remove] and [doReparse] change.
   */
  lateinit var dummyFile: PsiFile

  private var insertOffset = 0

  val project: Project
    get() = fixture.project

  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Inserts [s] at the edit point, reparses the range, checks the tree, and moves the edit point to the end of [s].
   */
  fun insert(@NonNls s: String) {
    runInEdtAndWait {
      CommandProcessor.getInstance().executeCommand(project, {
        ApplicationManager.getApplication().runWriteAction {
          val oldText = dummyFile.text
          val expectedNewText = oldText.substring(0, insertOffset) + s + oldText.substring(insertOffset)
          doReparseAndCheck(s, expectedNewText, 0)
          insertOffset += s.length
        }
      }, "asd", null)
    }
  }

  fun moveEditPointRight(count: Int) {
    insertOffset += count
  }

  fun setEditPoint(pos: Int) {
    insertOffset = pos
  }

  /**
   * Removes [count] characters before the edit point, reparses the range, checks the tree, and moves the edit point back.
   */
  fun remove(count: Int) {
    runInEdtAndWait {
      val oldText = dummyFile.text
      val expectedNewText = oldText.substring(0, insertOffset - count) + oldText.substring(insertOffset)
      doReparseAndCheck("", expectedNewText, count)
      insertOffset -= count
    }
  }

  private fun doReparseAndCheck(s: String, expectedNewText: String, length: Int) {
    doReparse(s, length)
    val foundStructure = DebugUtil.treeToString(dummyFile.node, true)
    val psiFile = createDummyFile(name + "." + fileType!!.defaultExtension, expectedNewText)
    val expectedStructure = DebugUtil.treeToString(psiFile.node, true)
    assertEquals(expectedStructure, foundStructure)
    assertEquals(expectedNewText, dummyFile.text, "Reparse tree should be equal to the document")
  }

  /**
   * Creates a non-physical file with [text]. A [DummyFileFactory] of [reparseFixture] can replace the file.
   */
  fun createDummyFile(fileName: String, text: String): PsiFile {
    return dummyFileFactory?.createDummyFile(this, fileName, text) ?: createDefaultDummyFile(fileName, text)
  }

  /**
   * Creates a non-physical file with [text], of the file type that [fileName] has.
   */
  fun createDefaultDummyFile(fileName: String, text: String): PsiFile {
    val type = FileTypeRegistry.getInstance().getFileTypeByFileName(fileName)
    return PsiFileFactory.getInstance(project).createFileFromText(fileName, type, text)
  }

  /**
   * Replaces [length] characters before the edit point with [s] in [dummyFile] with [BlockSupport.reparseRange].
   */
  fun doReparse(s: String, length: Int) {
    runInEdtAndWait {
      CommandProcessor.getInstance().executeCommand(project, {
        ApplicationManager.getApplication().runWriteAction {
          BlockSupport.getInstance(project).reparseRange(dummyFile, insertOffset - length, insertOffset, s)
        }
      }, "asd", null)
    }
  }

  /**
   * Creates [dummyFile] with the text [prefix] and [suffix], and sets the edit point between them.
   */
  fun prepareFile(@NonNls prefix: String, @NonNls suffix: String) {
    runInEdtAndWait {
      dummyFile = createDummyFile(name + "." + fileType!!.defaultExtension, prefix + suffix)
    }
    insertOffset = prefix.length
  }

  /**
   * Creates the files of [ReparseFixture.createDummyFile] in place of the default non-physical file.
   */
  fun interface DummyFileFactory {
    /**
     * Returns the file for [fileName] and [text]. Call [ReparseFixture.createDefaultDummyFile] for the default file.
     */
    fun createDummyFile(reparse: ReparseFixture, fileName: String, text: String): PsiFile
  }
}

/**
 * Creates a [ReparseFixture] for one test. Declare it as an instance field.
 *
 * [fileType] is the initial [ReparseFixture.fileType]. [dummyFileFactory] creates the files of [ReparseFixture.createDummyFile].
 * The fixture turns off the automatic highlighting for the test.
 */
@TestOnly
@JvmOverloads
fun reparseFixture(
  projectFixture: TestFixture<LightJavaProject>,
  fileType: FileType? = null,
  dummyFileFactory: ReparseFixture.DummyFileFactory? = null,
): TestFixture<ReparseFixture> = testFixture("reparseFixture") { context ->
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  // The automatic highlighting can read the PSI in the background at a moment that breaks the assumptions of the tests.
  withContext(Dispatchers.EDT) {
    DaemonCodeAnalyzer.getInstance(fixture.project).disableUpdateByTimer(fixture.testRootDisposable)
  }
  val reparse = ReparseFixture(fixture, context.extensionContext.requiredTestMethod.name, dummyFileFactory)
  reparse.fileType = fileType
  initialized(reparse) {}
}
