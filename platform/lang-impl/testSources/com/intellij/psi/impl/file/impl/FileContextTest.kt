// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.*
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.rootManager
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class FileContextTest {
  companion object {
    private val projectFixture = projectFixture().withSharedSourceEnabled()

    private val module1 = projectFixture.moduleFixture("src1")
    private val module2 = projectFixture.moduleFixture("src2")

    private val sourceRoot = sharedSourceRootFixture(module1, module2)
  }

  private val fileFixture = sourceRoot.virtualFileFixture("TestCommon.txt", "Test file Common")

  private val virtualFile by lazy { fileFixture.get() }
  private val project by lazy { projectFixture.get() }
  private val psiManager by lazy { PsiManager.getInstance(project) }
  private val contextManager by lazy { CodeInsightContextManagerImpl.getInstanceImpl(project) }

  private fun findPsiFile(): PsiFile = requireNotNull(psiManager.findFile(virtualFile))
  private fun findPsiFile(context: CodeInsightContext): PsiFile = requireNotNull(psiManager.findFile(virtualFile, context))

  @Test
  fun testAnyContextByDefault() = runBlocking {
    readAction {
      val file = findPsiFile()
      val rawContext = contextManager.getCodeInsightContextRaw(file.viewProvider)
      assertContextsEqual(anyContext(), rawContext)
    }
  }

  @Test
  fun testContextIsInferred() = runBlocking {
    readAction {
      val file = findPsiFile()
      assertNotEquals(anyContext(), file.codeInsightContext)
    }
  }

  @Test
  fun testContextIsCorrectlySet() = runBlocking {
    val context = module1.moduleContext()
    readAction {
      val file = findPsiFile(context)
      assertContextsEqual(context, file.codeInsightContext)
    }
  }

  @Test
  fun testRawContextIsCorrectlySet() = runBlocking {
    val context = module1.moduleContext()
    readAction {
      val file = findPsiFile(context)
      assertContextsEqual(context, contextManager.getCodeInsightContextRaw(file.viewProvider))
    }
  }

  @Test
  fun testAnyContextIsPromotedToExactContext() = runBlocking {
    val context1 = module1.moduleContext()

    readAction {
      val file = findPsiFile()
      val rawContext = contextManager.getCodeInsightContextRaw(file.viewProvider)

      assertContextsEqual(anyContext(), rawContext)

      val file1= findPsiFile(context1)
      assertEquals(file1, file)

      val rawContext1 = contextManager.getCodeInsightContextRaw(file1.viewProvider)
      assertContextsEqual(context1, rawContext1)
    }
  }

  @Test
  fun testTwoContextsForFile() = runBlocking {
    val context1 = module1.moduleContext()
    val context2 = module2.moduleContext()

    readAction {
      val file = findPsiFile()
      val rawContext = contextManager.getCodeInsightContextRaw(file.viewProvider)
      assertContextsEqual(anyContext(), rawContext)

      val file1= findPsiFile(context1)
      val file2= findPsiFile(context2)

      assertEquals(file1, file)
      assertNotEquals(file1, file2)

      val rawContext1 = contextManager.getCodeInsightContextRaw(file1.viewProvider)
      val rawContext2 = contextManager.getCodeInsightContextRaw(file2.viewProvider)
      assertContextsEqual(context1, rawContext1)
      assertContextsEqual(context2, rawContext2)
    }
  }
}

private fun assertContextsEqual(expectedContext: CodeInsightContext, actualContext: CodeInsightContext) {
  assertEquals(expectedContext, actualContext) { "Unexpected context: ${actualContext.asText()}. Expected: ${expectedContext.asText()}" }
}

private fun CodeInsightContext.asText() = when (this) {
  anyContext() -> "AnyContext"
  is ModuleContext -> "ModuleContext(${this.getModule()!!.name})"
  else -> this.toString()
}

fun sharedSourceRootFixture(vararg moduleFixtures: TestFixture<Module>): TestFixture<PsiDirectory> = testFixture("shared-source-root-fixture") {
  require(moduleFixtures.isNotEmpty())

  for (fixture in moduleFixtures) {
    fixture.init()
  }

  val firstFixture = moduleFixtures.first()
  val sharedSourceRoot = firstFixture.sourceRootFixture()
  val root = sharedSourceRoot.init().virtualFile

  // make sharedSourceRoot also the root of module 2
  edtWriteAction {
    for (fixture in moduleFixtures) {
      if (fixture === firstFixture) continue

      val module = fixture.get()
      val modifiableModel = module.rootManager.modifiableModel
      val contentRoot = modifiableModel.addContentEntry(root)
      contentRoot.addSourceFolder(root, false)
      modifiableModel.commit()
    }
  }

  initialized(sharedSourceRoot.init()) {
    // the root directory is deleted by the the sourceRootFixture of the first module.
    // todo IJPL-339 do we need to remove entries from modules?
  }
}

internal suspend fun TestFixture<Module>.moduleContext(): CodeInsightContext {
  val module = this.get()
  val contextBridge = ProjectModelContextBridge.getInstanceAsync(module.project)
  val context = readAction { requireNotNull(contextBridge.getContext(module)) }
  return context
}
