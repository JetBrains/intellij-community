// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.versioning

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.CodeInsightContextManagerImpl
import com.intellij.codeInsight.multiverse.ProjectModelContextBridge
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.PsiDirectory
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.util.PsiVersioningService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestApplication
internal class MultiverseFileViewProviderCacheVersioningConsistencyTest {
  private val tempDir = tempPathFixture()
  private val projectFixture = projectFixture(tempDir, openAfterCreation = true).withSharedSourceEnabled()
  private val module = projectFixture.moduleFixture("basic")
  private val sharedModule = projectFixture.moduleFixture("shared")
  private val sharedSourceRoot = sharedSourceRootFixture(module, sharedModule)
  private val sharedPsiFileFixture = sharedSourceRoot.psiFileFixture("Shared.java", """
    public class Shared {
    }
  """.trimIndent())

  private val project by projectFixture
  private val sharedPsiFile by sharedPsiFileFixture

  @Test
  fun `cached providers are not reassigned while reanimated in frozen PSI`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val psiManager = PsiManagerEx.getInstanceEx(project)
    val fileManager = psiManager.fileManagerEx
    val contextManager = CodeInsightContextManagerImpl.getInstanceImpl(project)
    val sharedVirtualFile = sharedPsiFile.virtualFile
    val context1 = moduleContext(module.get())
    val context2 = moduleContext(sharedModule.get())

    val (file1, file2) = readAction {
      val file1 = psiManager.findFile(sharedVirtualFile, context1)!!
      val file2 = psiManager.findFile(sharedVirtualFile, context2)!!
      Assertions.assertNotSame(file1.viewProvider, file2.viewProvider)
      file1 to file2
    }

    removeSharedSourceRootFromModule(sharedModule.get(), sharedSourceRoot.get())

    readAction {
      assertEquals(listOf(context1), contextManager.getCodeInsightContexts(sharedVirtualFile))
    }

    backgroundWriteAction {
      fileManager.possiblyInvalidatePhysicalPsi()
    }

    PsiVersioningService.freezePsiVersion {
      ThreadingAssertions.assertNoReadAccess()

      val providers = fileManager.findCachedViewProviders(sharedVirtualFile)
      assertTrue(file1.viewProvider in providers)
      assertTrue(file2.viewProvider in providers)
      assertEquals(context2, contextManager.getCodeInsightContextRaw(file2.viewProvider))
    }

    readAction {
      val providers = fileManager.findCachedViewProviders(sharedVirtualFile)
      assertTrue(file1.viewProvider in providers)
      Assertions.assertFalse(file2.viewProvider in providers)
    }
  }

  private suspend fun moduleContext(module: Module): CodeInsightContext {
    val contextBridge = ProjectModelContextBridge.getInstance(project)
    return readAction {
      requireNotNull(contextBridge.getContext(module))
    }
  }

  private suspend fun removeSharedSourceRootFromModule(module: Module, sharedSourceRoot: PsiDirectory) {
    edtWriteAction {
      ModuleRootModificationUtil.updateModel(module) { model ->
        val contentRoot = model.contentEntries.firstOrNull { it.file == sharedSourceRoot.virtualFile }
        if (contentRoot != null) {
          model.removeContentEntry(contentRoot)
        }
      }
    }
  }
}

private fun sharedSourceRootFixture(vararg moduleFixtures: TestFixture<Module>): TestFixture<PsiDirectory> = testFixture("shared-source-root-fixture") {
  require(moduleFixtures.isNotEmpty())

  for (fixture in moduleFixtures) {
    fixture.init()
  }
  val firstFixture = moduleFixtures.first()
  val sharedSourceRoot = firstFixture.sourceRootFixture()
  val directory = sharedSourceRoot.init()
  val root = directory.virtualFile

  edtWriteAction {
    for (fixture in moduleFixtures.drop(1)) {
      val module = fixture.get()
      val modifiableModel = module.rootManager.modifiableModel
      val contentRoot = modifiableModel.addContentEntry(root)
      contentRoot.addSourceFolder(root, false)
      modifiableModel.commit()
    }
  }

  initialized(directory) {
    edtWriteAction {
      for (fixture in moduleFixtures.drop(1)) {
        val module = fixture.get()
        if (!module.isDisposed) {
          ModuleRootModificationUtil.updateModel(module) { model ->
            val contentRoot = model.contentEntries.firstOrNull { it.file == root }
            if (contentRoot != null) {
              model.removeContentEntry(contentRoot)
            }
          }
        }
      }
    }
  }
}
