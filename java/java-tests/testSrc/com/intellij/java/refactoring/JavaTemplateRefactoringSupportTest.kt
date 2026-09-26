// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.java.impl.refactorings.template.JavaTemplateRefactoringSupport
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.inline.InlineLocalHandler
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

@TestApplication
@Timeout(30)
internal class JavaTemplateRefactoringSupportTest {
  companion object {
    private val project = projectFixture(openAfterCreation = true)
  }

  @Test
  fun javaWorksWithoutTemplateExtensions(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    ExtensionTestUtil.maskExtensions(JavaTemplateRefactoringSupport.EP_NAME, emptyList(), disposable)
    readAction {
      assertTrue(JavaTemplateRefactoringSupport.isClassRefactoringAllowed(javaClass()))
      assertTrue(JavaTemplateRefactoringSupport.isClassRefactoringAllowed(null))
      assertTrue(JavaTemplateRefactoringSupport.isInplaceIntroduceVariableAllowed(expression()))
      assertFalse(JavaTemplateRefactoringSupport.isInlineRefactoringSupported(JavaLanguage.INSTANCE))
      assertTrue(InlineLocalHandler().isEnabledForLanguage(JavaLanguage.INSTANCE))
      assertFalse(InlineLocalHandler().isEnabledForLanguage(Language.ANY))
    }
  }

  @Test
  fun appliesAllExtensions(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val neutral = object : JavaTemplateRefactoringSupport {}
    val specialized = object : JavaTemplateRefactoringSupport {
      override fun allowsClassRefactoring(psiClass: PsiClass): Boolean = false
      override fun supportsInlineRefactoring(language: Language): Boolean = language == Language.ANY
      override fun allowsInplaceIntroduceVariable(expression: PsiExpression): Boolean = false
    }
    ExtensionTestUtil.maskExtensions(JavaTemplateRefactoringSupport.EP_NAME, listOf(neutral, specialized, neutral), disposable)
    readAction {
      assertFalse(JavaTemplateRefactoringSupport.isClassRefactoringAllowed(javaClass()))
      assertTrue(JavaTemplateRefactoringSupport.isClassRefactoringAllowed(null))
      assertFalse(JavaTemplateRefactoringSupport.isInplaceIntroduceVariableAllowed(expression()))
      assertTrue(InlineLocalHandler().isEnabledForLanguage(Language.ANY))
      assertTrue(InlineLocalHandler().isEnabledForLanguage(JavaLanguage.INSTANCE))
    }
  }

  private fun javaClass(): PsiClass =
    (PsiFileFactory.getInstance(project.get()).createFileFromText("Example.java", JavaFileType.INSTANCE, "class Example {}")
      as PsiJavaFile).classes.single()

  private fun expression(): PsiExpression = JavaPsiFacade.getElementFactory(project.get()).createExpressionFromText("1 + 2", null)
}
