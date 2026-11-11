// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiManager
import com.intellij.refactoring.move.moveFilesOrDirectories.JavaMoveFilesOrDirectoriesHandler
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.utils.vfs.deleteRecursively
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MoveClassToNonIndexableDirTest : LightJavaCodeInsightFixtureTestCase5() {
  @Test
  fun testMoveClassToNonIndexableDir() = timeoutRunBlocking {
    val source = "package p; class Main {}"
    fixture.addFileToProject("src/Main.java", source)
    val javaClass = readAction { fixture.findClass("p.Main") }

    val nonIndexableDir = fixture.tempDirFixture.findOrCreateDir("nonIndexable")
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
      writeAction { nonIndexableDir.deleteRecursively() }
    }
  }
}
