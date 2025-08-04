// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.project.stateStore
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.utils.vfs.createFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class FileContextInChangingModuleTest {
  companion object {
    private val projectFixture = projectFixture().withSharedSourceEnabled()
  }

  private val project get() = projectFixture.get()

  @Test
  fun `psi file gets invalided after its context module is removed`() = timeoutRunBlocking {
    val projectBasePath = project.stateStore.projectBasePath
    val projectRoot = VfsUtil.findFile(projectBasePath, false)!!

    val module1 = performModuleUpdate { model ->
      model.newModule(projectBasePath, EmptyModuleType.getInstance().id).also {
        PsiTestUtil.addSourceRoot(it, projectRoot)
      }
    }

    val vFile = writeAction {
      projectRoot.createFile("foo.txt")
    }

    val psiFile = readAction {
      PsiManager.getInstance(project).findFile(vFile)!!
    }

    val context = readAction { psiFile.codeInsightContext }
    assertEquals(module1, (context as? ModuleContext)?.getModule())

    val module2 = performModuleUpdate { model ->
      model.disposeModule(module1)
      model.newModule(projectBasePath, EmptyModuleType.getInstance().id).also {
        PsiTestUtil.addSourceRoot(it, projectRoot)
      }
    }

    val psiFile2 = readAction {
      PsiManager.getInstance(project).findFile(vFile)!!
    }

    val context2 = readAction { psiFile2.codeInsightContext }
    assertEquals(module2, (context2 as? ModuleContext)?.getModule())
  }

  private suspend fun <T> performModuleUpdate(block: (ModifiableModuleModel) -> T): T {
    val model = ModuleManager.getInstanceAsync(project).getModifiableModel()
    val result = block(model)

    writeAction {
      model.commit()
    }

    return result
  }
}