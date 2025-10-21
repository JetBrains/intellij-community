// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.ModuleContext
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.codeInsight.multiverse.defaultContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.vfs.createFile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

@TestApplication
internal class FileInvalidationTest {
  private val projectFixture = multiverseProjectFixture(withSharedSourceEnabled = true) {}

  private val project get() = projectFixture.get()

  @Test
  fun `test default context invalidates`() = timeoutRunBlocking {
    val root = readAction { VfsUtil.findFile(Path(project.basePath!!), false)!! }
    val virtualFile = writeAction { root.createFile("foo.txt") }

    val psiFileDefaultContext = readAction { PsiManager.getInstance(project).findFile(virtualFile)!! }

    Assertions.assertEquals(defaultContext(), readAction { psiFileDefaultContext.codeInsightContext })

    val module1 = PsiTestUtil.addModule(project, ModuleType.EMPTY, "module1", root)

    val psiFileModuleContext = readAction { PsiManager.getInstance(project).findFile(virtualFile)!! }

    val moduleContext = readAction { psiFileModuleContext.codeInsightContext }
    Assertions.assertTrue(moduleContext is ModuleContext && moduleContext.getModule()!!.name == "module1")

    val module2 = PsiTestUtil.addModule(project, ModuleType.EMPTY, "module2", root)

    val psiFileModuleContext2 = readAction { PsiManager.getInstance(project).findFile(virtualFile)!! }
    val moduleContext2 = readAction { psiFileModuleContext2.codeInsightContext }
    Assertions.assertTrue(moduleContext2 is ModuleContext && moduleContext2.getModule()!!.name == "module1")

    PsiTestUtil.removeAllRoots(module1, null)

    val psiFileModuleContext3 = readAction { PsiManager.getInstance(project).findFile(virtualFile)!! }
    val moduleContext3 = readAction { psiFileModuleContext3.codeInsightContext }
    Assertions.assertTrue(moduleContext3 is ModuleContext && moduleContext3.getModule()!!.name == "module2")
  }
}