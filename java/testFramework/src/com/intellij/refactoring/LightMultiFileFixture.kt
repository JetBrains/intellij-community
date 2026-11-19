// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ThrowableRunnable
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertNotNull
import java.nio.file.Path

/**
 * The fixture of the tests that copy a directory of the test data to the project, change it, and compare the result.
 * Create it with [lightMultiFileFixture].
 *
 * [doTest] copies `<test name>/before` to the source root, runs the action, and compares the source root with `<test name>/after`.
 * The action and the comparison run in one block on the EDT, so a refactoring runs from its start to the last check there.
 * A test that reads the PSI outside [doTest] needs its own read action.
 */
@TestOnly
class LightMultiFileFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /**
   * Returns the name of the test method without the `test` prefix, as [LightJavaCodeInsightTestFixture.getTestName] does.
   */
  fun getTestName(lowercaseFirstLetter: Boolean): String = fixture.getTestName(lowercaseFirstLetter)

  /**
   * Runs [performAction] on the directory of the test name with a lower case first letter.
   */
  fun doTest(performAction: ThrowableRunnable<out Exception>) {
    doTest(performAction, getTestName(true))
  }

  fun doTest(performAction: ThrowableRunnable<out Exception>, lowercaseFirstLetter: Boolean) {
    doTest(performAction, getTestName(lowercaseFirstLetter))
  }

  /**
   * Copies `[testName]/before` to the source root, runs [performAction], and compares the source root with `[testName]/after`.
   * A checked exception of [performAction] comes out as a [RuntimeException].
   */
  fun doTest(performAction: ThrowableRunnable<out Exception>, testName: String) {
    val actualDirectory = fixture.copyDirectoryToProject("$testName/before", "")
    runInEdtAndWait {
      try {
        performAction.run()

        val expectedPath = FileUtil.toSystemIndependentName(fixture.testDataPath) + testName + "/after"
        val rootAfter = StandardFileSystems.local().findFileByPath(expectedPath)
        assertNotNull(rootAfter)

        VfsUtilCore.visitChildrenRecursively(actualDirectory, object : VirtualFileVisitor<Void>() {
          override fun visitFile(file: VirtualFile): Boolean {
            file.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, Path.of(expectedPath, VfsUtil.getRelativePath(file, actualDirectory)).toString())
            return super.visitFile(file)
          }
        })
        PlatformTestUtil.assertDirectoriesEqual(rootAfter!!, actualDirectory)
      }
      catch (e: RuntimeException) {
        throw e
      }
      catch (e: Exception) {
        throw RuntimeException(e)
      }
    }
  }

  /**
   * Moves the file [fileToMove] to the directory [targetDirName] with [MoveFilesOrDirectoriesProcessor] in [doTest].
   * Both paths are relative to the source root.
   */
  fun doMoveFileTest(targetDirName: String, fileToMove: String) {
    doTest(ThrowableRunnable {
      val child = fixture.findFileInTempDir(fileToMove)
      assertNotNull(child, "File $fileToMove not found")
      val file = PsiManager.getInstance(fixture.project).findFile(child!!)
      assertNotNull(file)

      val child1 = fixture.findFileInTempDir(targetDirName)
      assertNotNull(child1, "File $targetDirName not found")
      val targetDirectory = PsiManager.getInstance(fixture.project).findDirectory(child1!!)
      assertNotNull(targetDirectory)

      MoveFilesOrDirectoriesProcessor(fixture.project, arrayOf<PsiElement>(file!!), targetDirectory!!, false, false, null, null).run()

      val index = fileToMove.lastIndexOf("/")
      val psiFile = targetDirectory.findFile(if (index == -1) fileToMove else fileToMove.substring(index + 1))
      assertNotNull(psiFile)
    })
  }
}

/**
 * Creates a [LightMultiFileFixture] for one test. Declare it as an instance field.
 *
 * [testDataPath] is the directory with a subdirectory for each test. It ends with `/`.
 */
@TestOnly
fun lightMultiFileFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String,
): TestFixture<LightMultiFileFixture> = testFixture("lightMultiFileFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  fixture.testDataPath = testDataPath
  initialized(LightMultiFileFixture(fixture)) {}
}
