// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.impl.file.impl.moduleContext
import com.intellij.psi.impl.file.impl.sharedSourceRootFixture
import com.intellij.psi.search.ActualCodeInsightContextInfo
import com.intellij.psi.search.ActualContextFileInfo
import com.intellij.psi.search.CodeInsightContextAwareSearchScope
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
 * Tests for the [RootContainerScope.getFileInfo] implementation.
 *
 * The general contract of [com.intellij.psi.search.createContainingContextFileInfo] is covered by
 * `com.intellij.psi.search.CodeInsightContextAwareSearchScopesTest` (in `intellij.platform.tests`);
 * this class exercises the real [RootContainerScope] driven by
 * [com.intellij.openapi.roots.ProjectFileIndex] and [RootContainer]. Library-related cases
 * (including the IDEA-389519 regression guard) live in [RootContainerScopeGetFileInfoLibrariesTest].
 */
@TestApplication
internal class RootContainerScopeGetFileInfoTest {
  companion object {
    // RootContainerScope.getFileInfo requires shared-source support: ClassicRootContainer.getRootDescriptor
    // throws UnsupportedOperationException, only MultiverseRootContainer implements the lookup.
    private val project = projectFixture().withSharedSourceEnabled()

    private val module1 = project.moduleFixture("RCSGFIT_m1")
    private val module2 = project.moduleFixture("RCSGFIT_m2")

    private val src1 = module1.sourceRootFixture()
    private val src2 = module2.sourceRootFixture()
    private val sharedSrc = sharedSourceRootFixture(module1, module2)

    private val fileInM1 = src1.psiFileFixture("A.txt", "")
    private val fileInM2 = src2.psiFileFixture("B.txt", "")
    private val fileShared = sharedSrc.psiFileFixture("Shared.txt", "")
  }

  @Test
  fun `own-module source file returns ActualContextFileInfo with the module context`(): Unit = timeoutRunBlocking {
    val module = module1.get()
    val expected = module1.moduleContext()
    readAction {
      val info = module.dependenciesScopeContextInfo().getFileInfo(fileInM1.get().virtualFile)
      val actual = assertInstanceOf(ActualContextFileInfo::class.java, info)
      assertEquals(setOf(expected), actual.contexts.toSet())
    }
  }

  @Test
  fun `sibling-module source file returns DoesNotContainFileInfo`(): Unit = timeoutRunBlocking {
    val module = module1.get()
    readAction {
      val info = module.dependenciesScopeContextInfo().getFileInfo(fileInM2.get().virtualFile)
      assertInstanceOf(DoesNotContainFileInfo::class.java, info)
    }
  }

  @Test
  fun `shared source root file returns only the queried module's context`(): Unit = timeoutRunBlocking {
    val m1 = module1.get()
    val m2 = module2.get()
    val ctx1 = module1.moduleContext()
    val ctx2 = module2.moduleContext()
    val shared = fileShared.get().virtualFile
    readAction {
      val info1 = m1.dependenciesScopeContextInfo().getFileInfo(shared)
      val actual1 = assertInstanceOf(ActualContextFileInfo::class.java, info1)
      assertEquals(setOf(ctx1), actual1.contexts.toSet())

      val info2 = m2.dependenciesScopeContextInfo().getFileInfo(shared)
      val actual2 = assertInstanceOf(ActualContextFileInfo::class.java, info2)
      assertEquals(setOf(ctx2), actual2.contexts.toSet())
    }
  }

  @Test
  fun `file outside any project root returns DoesNotContainFileInfo`(): Unit = timeoutRunBlocking {
    val module = module1.get()
    val orphan: VirtualFile = LightVirtualFile("orphan.txt")
    readAction {
      val info = module.dependenciesScopeContextInfo().getFileInfo(orphan)
      assertInstanceOf(DoesNotContainFileInfo::class.java, info)
    }
  }

  @Test
  fun `ModuleWithDependentsScope reports the same getFileInfo result as ModuleWithDependenciesScope`(): Unit =
    timeoutRunBlocking {
      val module = module1.get()
      val expected = module1.moduleContext()
      readAction {
        val dependents = ModuleWithDependentsScope(module).getFileInfo(fileInM1.get().virtualFile)
        val actual = assertInstanceOf(ActualContextFileInfo::class.java, dependents)
        assertEquals(setOf(expected), actual.contexts.toSet())
      }
    }
}

private fun Module.dependenciesScopeContextInfo(): ActualCodeInsightContextInfo {
  val aware = getModuleWithDependenciesScope() as CodeInsightContextAwareSearchScope
  return aware.codeInsightContextInfo as ActualCodeInsightContextInfo
}
