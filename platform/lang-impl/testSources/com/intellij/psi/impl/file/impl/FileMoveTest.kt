// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.*
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.multiverseProjectFixture
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.fileOrDirInProjectFixture
import com.intellij.testFramework.junit5.fixture.moduleInProjectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class FileMoveTest {
  private val projectFixture = multiverseProjectFixture(openAfterCreation = true) {
    module("module1") {
      contentRoot("contentRoot1") {
        sourceRoot("src1") {
          file("A.java", "public class A {}")
        }
        sourceRoot("src1-2"){
        }
      }
    }
    module("module2") {
      contentRoot("contentRoot2") {
        sourceRoot("src2") {
        }
      }
    }
    module("module3") {
      contentRoot("contentRoot3") {
        sourceRoot("src34", "sharedRoot34") {
          file("B.java", "public class B {}")
        }
        sourceRoot("src34-2", "sharedRoot34-2") {
        }
      }
    }
    module("module4") {
      sharedSourceRoot("sharedRoot34")
      sharedSourceRoot("sharedRoot34-2")
      contentRoot("contentRoot4") {
        sourceRoot("src4") {
        }
      }
    }
  }

  private val project by projectFixture

  private val aJava by projectFixture.fileOrDirInProjectFixture("module1/contentRoot1/src1/A.java")
  private val bJava by projectFixture.fileOrDirInProjectFixture("module3/contentRoot3/src34/B.java")

  private val src2 by projectFixture.fileOrDirInProjectFixture("module2/contentRoot2/src2")
  private val src4 by projectFixture.fileOrDirInProjectFixture("module4/contentRoot4/src4")
  private val src12 by projectFixture.fileOrDirInProjectFixture("module1/contentRoot1/src1-2")

  private val module3 by projectFixture.moduleInProjectFixture("module3")
  private val module4 by projectFixture.moduleInProjectFixture("module4")

  @Test
  fun `file changes context after move`() = timeoutRunBlocking {
    val psiFile = aJava.findPsiFile()

    assertEquals("module1", psiFile.getModuleContextName())

    writeAction {
      aJava.move(this, src2)
    }

    readAction {
      assert(psiFile.isValid) { "file changed context after move => psiFile must not be invalidated" }
    }

    val movedPsiFile = aJava.findPsiFile()
    assertEquals("module2", movedPsiFile.getModuleContextName())
  }

  @Test
  fun `file changes context after move for any-context`() = timeoutRunBlocking {
    val psiFile = aJava.findPsiFile()

    val rawContext = CodeInsightContextManagerImpl.getInstanceImpl(project).getCodeInsightContextRaw(psiFile.viewProvider)
    assert(rawContext == anyContext())

    writeAction {
      aJava.move(this, src2)
    }

    readAction {
      assert(psiFile.isValid) { "file changed context after move => psiFile must not be invalidated" }
    }

    val movedPsiFile = aJava.findPsiFile()
    assertEquals("module2", movedPsiFile.getModuleContextName())
  }


  @Test
  fun `shared file changes context after move`() = timeoutRunBlocking {
    val psiFile3 = bJava.findPsiFile(module3.asContext())
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    writeAction {
      bJava.move(this, src2)
    }

    readAction {
      assert(psiFile3.isValid xor psiFile4.isValid) { "one of the files survives" }
    }

    val movedPsiFile = bJava.findPsiFile()
    assertEquals("module2", movedPsiFile.getModuleContextName())
  }

  @Test
  fun `shared file changes context after move when one context survives`() = timeoutRunBlocking {
    val psiFile3 = bJava.findPsiFile(module3.asContext())
    val psiFile4 = bJava.findPsiFile(module4.asContext())

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    writeAction {
      bJava.move(this, src4)
    }

    readAction {
      assert(!psiFile3.isValid) { "module3 is not relevant anymore" }
      assert(psiFile4.isValid) { "module4 is still relevant" }
    }

    val movedPsiFile = bJava.findPsiFile()
    assertEquals("module4", movedPsiFile.getModuleContextName())
  }

  @Test
  fun `shared file moves and all contexts survive`() = timeoutRunBlocking {
    val module3 = ModuleManager.getInstance(project).findModuleByName("module3")!!
    val module4 = ModuleManager.getInstance(project).findModuleByName("module4")!!
    val module3Context = ProjectModelContextBridge.getInstance(project).getContext(module3)!!
    val module4Context = ProjectModelContextBridge.getInstance(project).getContext(module4)!!

    val psiFile3 = bJava.findPsiFile(module3Context)
    val psiFile4 = bJava.findPsiFile(module4Context)

    assertEquals("module3", psiFile3.getModuleContextName())
    assertEquals("module4", psiFile4.getModuleContextName())

    writeAction {
      bJava.move(this, src4)
    }

    readAction {
      assert(!psiFile3.isValid) { "module3 is not relevant anymore" }
      assert(psiFile4.isValid) { "module4 is still relevant" }
    }

    val movedPsiFile = bJava.findPsiFile()
    assertEquals("module4", movedPsiFile.getModuleContextName())
  }


  @Test
  fun `file survives move`() = timeoutRunBlocking {
    val psiFile = aJava.findPsiFile()

    assertEquals("module1", psiFile.getModuleContextName())

    writeAction {
      aJava.move(this, src12)
    }

    readAction {
      assert(psiFile.isValid)
    }

    assertEquals("module1", psiFile.getModuleContextName())
  }

  private suspend fun VirtualFile.findPsiFile(): PsiFile {
    return readAction {
      PsiManager.getInstance(projectFixture.get()).findFile(this) ?: throw IllegalStateException("PsiFile not found: $this")
    }
  }

  private suspend fun VirtualFile.findPsiFile(context: CodeInsightContext): PsiFile {
    return readAction {
      PsiManager.getInstance(projectFixture.get()).findFile(this, context) ?: throw IllegalStateException("PsiFile not found: $this")
    }
  }

  private suspend fun PsiFile.getModuleContextName(): String {
    return readAction {
      (codeInsightContext as ModuleContext).getModule()!!.name
    }
  }

  private fun Module.asContext(): ModuleContext = ProjectModelContextBridge.getInstance(project).getContext(this)!!
}
