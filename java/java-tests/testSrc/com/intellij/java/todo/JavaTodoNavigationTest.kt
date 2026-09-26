// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.todo

import com.intellij.model.psi.PsiSymbolReferenceService
import com.intellij.navigation.NavigatableSymbol
import com.intellij.openapi.application.smartReadAction
import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.psiFileFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@TestApplication
@Timeout(30)
internal class JavaTodoNavigationTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
    private val sourceRoot = projectFixture.moduleFixture("src").sourceRootFixture()
  }

  private val file by sourceRoot.psiFileFixture("Example.java", "class Example {} // TODO review")

  @Test
  @RegistryKey(key = "todo.navigation", value = "true")
  fun referencesResolveWhenEnabled(): Unit = timeoutRunBlocking {
    smartReadAction(projectFixture.get()) {
      val comment = requireNotNull(PsiTreeUtil.findChildOfType(file, PsiComment::class.java))
      val reference = PsiSymbolReferenceService.getService().getReferences(comment).single()
      assertEquals("TODO", reference.rangeInElement.substring(comment.text))
      val symbol = reference.resolveReference().single() as NavigatableSymbol
      assertEquals(1, symbol.getNavigationTargets(projectFixture.get()).size)
    }
  }

  @Test
  @RegistryKey(key = "todo.navigation", value = "false")
  fun referencesAreAbsentWhenDisabled(): Unit = timeoutRunBlocking {
    smartReadAction(projectFixture.get()) {
      val comment = requireNotNull(PsiTreeUtil.findChildOfType(file, PsiComment::class.java))
      assertTrue(PsiSymbolReferenceService.getService().getReferences(comment).isEmpty())
    }
  }
}
