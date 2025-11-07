// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import org.junit.jupiter.api.Test

@TestApplication
internal class CommitMultiverseFileTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true).withSharedSourceEnabled()

    private val module1 = projectFixture.moduleFixture("CommitMultiverseFileTest_src1")
    private val module2 = projectFixture.moduleFixture("CommitMultiverseFileTest_src2")

    private val sourceRoot = sharedSourceRootFixture(module1, module2)
  }

  private val virtualFile by sourceRoot.virtualFileFixture("TestCommon.java", "class A {}")

  @Test
  fun `test commit document reparses both psi versions`() = timeoutRunBlocking {
    val project = projectFixture.get()

    IndexingTestUtil.waitUntilIndexesAreReady(project)

    val (psiFile1, psiFile2) = readAction {
      val psiManager = PsiManagerEx.getInstanceEx(project)
      val contextBridge = ProjectModelContextBridge.getInstance(project)
      val context1 = contextBridge.getContext(module1.get())!!
      val context2 = contextBridge.getContext(module2.get())!!
      psiManager.findFile(virtualFile, context1)!! to psiManager.findFile(virtualFile, context2)!!
    }

    readAction {
      ensureParsed(psiFile1)
      ensureParsed(psiFile2)
    }

    val document = readAction {
      FileDocumentManager.getInstance().getDocument(virtualFile)!!
    }

    writeAction {
      document.setText("class B {}")
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    readAction {
      PsiUtilCore.ensureValid(psiFile1)
      PsiUtilCore.ensureValid(psiFile2)

      assert(psiFile1.text == "class B {}")
      assert(psiFile2.text == "class B {}")
    }
  }

  private fun ensureParsed(file: PsiFile) {
    file.accept(object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        element.acceptChildren(this)
      }
    })
  }
}