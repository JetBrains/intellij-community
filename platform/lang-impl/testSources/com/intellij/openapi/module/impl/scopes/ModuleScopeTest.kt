// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.codeInsight.multiverse.anyContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.withSharedSourceEnabled
import com.intellij.psi.impl.file.impl.moduleContext
import com.intellij.psi.impl.file.impl.sharedSourceRootFixture
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.contains
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import org.junit.jupiter.api.Test

@TestApplication
internal class ModuleScopeTest {
  companion object {
    private val project = projectFixture().withSharedSourceEnabled()

    private val module1 = project.moduleFixture("ModuleScopeTest_src1")
    private val module2 = project.moduleFixture("ModuleScopeTest_src2")

    private val sourceRoot1 = module1.sourceRootFixture()
    private val sourceRoot2 = module2.sourceRootFixture()
    private val sourceRootCommon = sharedSourceRootFixture(module1, module2)

    private val psiFile1 = sourceRoot1.psiFileFixture("Test.txt", "Test file")
    private val psiFile2 = sourceRoot2.psiFileFixture("Test2.txt", "Test file 2")
    private val psiFileCommon = sourceRootCommon.psiFileFixture("ModuleScopeTestTestCommon.txt", "Test file Common")

    private val file1 get() = psiFile1.get().virtualFile
    private val file2 get() = psiFile2.get().virtualFile
    private val fileCommon get() = psiFileCommon.get().virtualFile
  }

  @Test
  fun testAnyContext() = timeoutRunBlocking {
    val scope = module1.moduleScope()

    assertScopeContains(file1, anyContext(), scope)
    assertScopeContains(fileCommon, anyContext(), scope)
    assertScopeDoesNotContain(file2, anyContext(), scope)
  }

  @Test
  fun testResolveScopeAndModule1Context() = timeoutRunBlocking {
    val scope1 = module1.moduleScope()
    val module1Context = module1.moduleContext()

    assertScopeContains(file1, module1Context, scope1)
    assertScopeContains(fileCommon, module1Context, scope1)
    //assertScopeDoesNotContain(file2, module1Context, scope1) todo IJPL-339 figure out if we should not allow file2 in module1context to exist in scope1
  }

  @Test
  fun testResolveScopeAndModule2Context() = timeoutRunBlocking {
    val scope2 = module2.moduleScope()
    val module2Context = module2.moduleContext()

    //assertScopeDoesNotContain(file1, module2Context, scope2) todo IJPL-339 figure out if we should not allow file1 in module2context to exist in scope2
    assertScopeContains(fileCommon, module2Context, scope2)
    assertScopeContains(file2, module2Context, scope2)
  }

  @Test
  fun testSharedFileAndScopes() = timeoutRunBlocking {
    val scope1 = module1.moduleScope()
    val scope2 = module2.moduleScope()

    val module1Context = module1.moduleContext()
    val module2Context = module2.moduleContext()

    assertScopeContains(fileCommon, module1Context, scope1)
    assertScopeDoesNotContain(fileCommon, module1Context, scope2)

    assertScopeContains(fileCommon, module2Context, scope2)
    assertScopeDoesNotContain(fileCommon, module2Context, scope1)
  }

  private suspend fun assertScopeContains(file: VirtualFile, context: CodeInsightContext, scope: SearchScope) {
    readAction {
      assert(scope.contains(file, context)) { "$file with $context not found in $scope" }
    }
  }

  private suspend fun assertScopeDoesNotContain(file: VirtualFile, context: CodeInsightContext, scope: SearchScope) {
    readAction {
      assert(!scope.contains(file, context)) { "$file with $context found in $scope" }
    }
  }
}

private fun TestFixture<Module>.moduleScope(): GlobalSearchScope = ModuleWithDependentsScope(this.get())