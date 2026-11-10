// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class LightFilePsiClassesChangedTest {
  private val project by projectFixture()

  @Test
  fun `the reset of a file type change keeps the PSI of a light file`() = timeoutRunBlocking {
    val file = LightVirtualFile("a.txt", PlainTextFileType.INSTANCE, "text")
    val before = findPsi(file)

    edtWriteAction { PsiManagerEx.getInstanceEx(project).fileManagerEx.processFileTypesChanged(true) }

    assertSame(before, findPsi(file))
  }

  @Test
  fun `the reset of the PSI classes drops the PSI of a light file`() = timeoutRunBlocking {
    val file = LightVirtualFile("a.txt", PlainTextFileType.INSTANCE, "text")
    val before = findPsi(file)

    edtWriteAction { PsiManagerEx.getInstanceEx(project).fileManagerEx.processPsiClassesChanged() }

    val after = findPsi(file)
    assertNotSame(before, after)
    assertTrue(readAction { !before.isValid })
    assertTrue(readAction { after.isValid })
  }

  private suspend fun findPsi(file: LightVirtualFile): PsiFile {
    return readAction { PsiManager.getInstance(project).findFile(file)!! }
  }
}
