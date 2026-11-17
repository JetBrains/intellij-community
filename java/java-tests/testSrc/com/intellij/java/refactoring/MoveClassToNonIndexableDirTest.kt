// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.vfs.readText
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveFilesOrDirectories.JavaMoveFilesOrDirectoriesHandler
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.deleteRecursively
import com.intellij.workspaceModel.ide.registerProjectRoot
import com.intellij.workspaceModel.ide.unregisterProjectRoot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class MoveClassToNonIndexableDirTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK)
  }

  private val fixture by lightJavaCodeInsightFixture(projectFixture)

  @Test
  fun testMoveClassToNonIndexableDir() = timeoutRunBlocking {
    val source = "package p; class Main {}"
    fixture.addFileToProject("Main.java", source)
    val javaClass = readAction { fixture.findClass("p.Main") }

    val nonIndexableDir = writeAction { projectFixture.get().sourceRoot.parent.createChildDirectory(null, "nonIndexable") }
    val nonIndexableUrl = fixture.project.serviceAsync<WorkspaceModel>().getVirtualFileUrlManager().storeAndGet(nonIndexableDir.url)
    registerProjectRoot(fixture.project, nonIndexableUrl)
    try {
      val nonIndexablePsi = readAction { PsiManager.getInstance(fixture.project).findDirectory(nonIndexableDir)!! }

      val handler = JavaMoveFilesOrDirectoriesHandler()
      assertTrue(readAction { handler.canMove(arrayOf(javaClass), nonIndexablePsi, null) })
      edtWriteAction { handler.doMove(fixture.project, arrayOf(javaClass), nonIndexablePsi, null) }

      val moved = nonIndexableDir.findChild("Main.java")
      checkNotNull(moved)
      assertEquals(source, moved.readText())
    }
    finally {
      unregisterProjectRoot(fixture.project, nonIndexableUrl)
      writeAction { nonIndexableDir.deleteRecursively() }
    }
  }
}
