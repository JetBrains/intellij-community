// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.impl.file.impl.moduleContext
import com.intellij.psi.impl.file.impl.sharedSourceRootFixture
import com.intellij.psi.search.ActualCodeInsightContextInfo
import com.intellij.psi.search.ActualContextFileInfo
import com.intellij.psi.search.DoesNotContainFileInfo
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

/**
 * Tests for [RootContainerScope.uniteWith], which exercises the [RootContainer] merge path.
 * The per-scope [RootContainerScope.getFileInfo] behavior is covered by [RootContainerScopeGetFileInfoTest].
 */
@TestApplication
internal class RootContainerScopeUnionTest {
  companion object {
    private val project = projectFixture().withSharedSourceEnabled()

    private val module1 = project.moduleFixture("RCSUT_m1")
    private val module2 = project.moduleFixture("RCSUT_m2")

    private val src1 = module1.sourceRootFixture()
    private val src2 = module2.sourceRootFixture()
    private val sharedSrc = sharedSourceRootFixture(module1, module2)

    private val fileInM1 = src1.psiFileFixture("A.txt", "")
    private val fileInM2 = src2.psiFileFixture("B.txt", "")
    private val fileShared = sharedSrc.psiFileFixture("Shared.txt", "")
  }

  @Test
  fun `united scope reports the own files of both modules with their contexts`(): Unit = timeoutRunBlocking {
    val ctx1 = module1.moduleContext()
    val ctx2 = module2.moduleContext()
    readAction {
      val united = unitedScopeContextInfo()

      val info1 = united.getFileInfo(fileInM1.get().virtualFile)
      val actual1 = assertInstanceOf(ActualContextFileInfo::class.java, info1)
      assertEquals(setOf(ctx1), actual1.contexts.toSet())

      val info2 = united.getFileInfo(fileInM2.get().virtualFile)
      val actual2 = assertInstanceOf(ActualContextFileInfo::class.java, info2)
      assertEquals(setOf(ctx2), actual2.contexts.toSet())
    }
  }

  @Test
  fun `united scope keeps one context for a root that both scopes contain`(): Unit = timeoutRunBlocking {
    // The merge keeps the first descriptor per root (todo IJPL-339), and uniteWith puts the argument scopes first.
    val ctx2 = module2.moduleContext()
    readAction {
      val united = unitedScopeContextInfo()
      val info = united.getFileInfo(fileShared.get().virtualFile)
      val actual = assertInstanceOf(ActualContextFileInfo::class.java, info)
      assertEquals(setOf(ctx2), actual.contexts.toSet())
    }
  }

  @Test
  fun `united scope does not contain a file outside any root`(): Unit = timeoutRunBlocking {
    val orphan: VirtualFile = LightVirtualFile("orphan.txt")
    readAction {
      val united = unitedScopeContextInfo()
      val info = united.getFileInfo(orphan)
      assertInstanceOf(DoesNotContainFileInfo::class.java, info)
    }
  }

  private fun unitedScopeContextInfo(): ActualCodeInsightContextInfo {
    val scope1 = module1.get().getModuleWithDependenciesScope() as RootContainerScope
    val scope2 = module2.get().getModuleWithDependenciesScope()
    val result = requireNotNull(scope1.uniteWith(listOf(scope2))) { "RootContainerScopes must be unitable" }
    val united = result.unitedScope as RootContainerScope
    return united.codeInsightContextInfo as ActualCodeInsightContextInfo
  }
}
