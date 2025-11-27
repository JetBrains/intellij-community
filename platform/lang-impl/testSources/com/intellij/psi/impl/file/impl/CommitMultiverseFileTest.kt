// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.*
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class CommitMultiverseFileTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true).withSharedSourceEnabled()

    private val module1 = projectFixture.moduleFixture("CommitMultiverseFileTest_src1")
    private val module2 = projectFixture.moduleFixture("CommitMultiverseFileTest_src2")

    private val sourceRoot = sharedSourceRootFixture(module1, module2)

    private val project by projectFixture

    private val psiManager by lazy { PsiManagerEx.getInstanceEx(project) }

    private val contextBridge by lazy { ProjectModelContextBridge.getInstance(project) }
    private val context1 by lazy { contextBridge.getContext(module1.get())!! }
    private val context2 by lazy { contextBridge.getContext(module2.get())!! }
  }

  private val virtualFile by sourceRoot.virtualFileFixture("TestCommon.java", "class A {}")
  private val files by sourceRoot.fileFixtures(50)

  @Test
  fun `test commit document reparses both psi versions`() = timeoutRunBlocking {
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

  /**
   * This test checks a situation:
   * a virtual file has a PSI file for context A.
   * The file text gets updated and is being asynchronously committed.
   * At the same time, another read-action requests a PSI file of this file with context B.
   * The requested PSI file must stay in sync with the first PSI file.
   *
   * When commit infra is not ready for this, around 10% of test runs fail.
   */
  @RepeatedTest(100)
  fun `test commit document and requesting psi for another context at the same time`() = timeoutRunBlocking(20.seconds) {
    IndexingTestUtil.waitUntilIndexesAreReady(project)

    // preparing psi files
    val psiFiles1 = readAction {
      files.map { psiManager.findFile(it, context1)!! }
    }

    readAction {
      for (file in psiFiles1) {
        ensureParsed(file)
      }
    }

    val documents = readAction {
      files.map { FileDocumentManager.getInstance().getDocument(it)!! }
    }

    // adding long suffix to all files
    writeAction {
      for (file in files) {
        Assertions.assertEquals(1, psiManager.fileManagerEx.findCachedViewProviders(file).size)
      }

      documents.forEach {
        CommandProcessor.getInstance().executeCommand(project, {
          it.insertString(it.textLength, longText)
        }, null, null)
      }
    }

    // requesting another psi file version, trying to do that concurrently with async commit
    val psiFiles2 = files.asReversed().mapIndexed { index, file ->
      async {
        delay(index.milliseconds) // waiting a bit to increase the chance of clashing between async commit and file request
        readAction {
          val psi = psiManager.findFile(file, context2)!!
          ensureParsed(psi)
          psi
        }
      }
    }.awaitAll()

    // waiting for commit to finish
    while (PsiDocumentManager.getInstance(project).hasUncommitedDocuments()) {
      delay(50)
    }

    // ensuring everything is in sync
    readAction {
      for (file in psiFiles1 + psiFiles2) {
        PsiUtilCore.ensureValid(file)
        file.viewProvider.contentsSynchronized()
      }
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

/**
 *
 * class A1 {
 * class A2 {
 * class A3 {
 * ...
 * }
 * }
 * }
 */
private val longText = buildString {
  val times = 20
  appendLine()
  repeat(times) {
    appendLine("class A$it {")
  }
  repeat(times) {
    appendLine("}")
  }
}

private fun TestFixture<PsiDirectory>.fileFixtures(number: Int) = testFixture {
  val files = (0..number).map {
    virtualFileFixture("file$it.java", "class A {}").init()
  }
  initialized(files) {}
}