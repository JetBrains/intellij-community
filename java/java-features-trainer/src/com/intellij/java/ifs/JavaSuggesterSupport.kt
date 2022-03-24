// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ifs

import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiJavaFileImpl
import com.intellij.psi.util.descendantsOfType
import training.featuresSuggester.SuggesterSupport
import training.featuresSuggester.getParentByPredicate
import training.featuresSuggester.getParentOfType

class JavaSuggesterSupport : SuggesterSupport {
  override fun isLoadedSourceFile(file: PsiFile): Boolean {
    return file is PsiJavaFileImpl && file.isContentsLoaded
  }

  override fun isIfStatement(element: PsiElement): Boolean {
    return element is PsiIfStatement
  }

  override fun isForStatement(element: PsiElement): Boolean {
    return element is PsiForStatement
  }

  override fun isWhileStatement(element: PsiElement): Boolean {
    return element is PsiWhileStatement
  }

  override fun isCodeBlock(element: PsiElement): Boolean {
    return element is PsiCodeBlock
  }

  override fun getCodeBlock(element: PsiElement): PsiElement? {
    return element.descendantsOfType<PsiCodeBlock>().firstOrNull()
  }

  override fun getContainingCodeBlock(element: PsiElement): PsiElement? {
    return element.getParentOfType<PsiCodeBlock>()
  }

  override fun getParentStatementOfBlock(element: PsiElement): PsiElement? {
    return element.parent?.parent
  }

  override fun getStatements(element: PsiElement): List<PsiElement> {
    return if (element is PsiCodeBlock) {
      element.statements.toList()
    }
    else {
      emptyList()
    }
  }

  override fun getTopmostStatementWithText(psiElement: PsiElement, text: String): PsiElement? {
    return psiElement.getParentByPredicate {
      isSupportedStatementToIntroduceVariable(it) && it.text.contains(text) && it.text != text
    }
  }

  override fun isSupportedStatementToIntroduceVariable(element: PsiElement): Boolean {
    return element is PsiStatement
  }

  override fun isPartOfExpression(element: PsiElement): Boolean {
    return element.getParentOfType<PsiExpression>() != null
  }

  override fun isExpressionStatement(element: PsiElement): Boolean {
    return element is PsiExpressionStatement
  }

  override fun isVariableDeclaration(element: PsiElement): Boolean {
    return element is PsiDeclarationStatement
  }

  override fun getVariableName(element: PsiElement): String? {
    return if (element is PsiDeclarationStatement) {
      val localVariable = element.declaredElements.firstOrNull() as? PsiLocalVariable
      localVariable?.name
    }
    else {
      null
    }
  }

  override fun isFileStructureElement(element: PsiElement): Boolean {
    return element is PsiField || element is PsiMethod || element is PsiClass
  }

  override fun isIdentifier(element: PsiElement): Boolean {
    return element is PsiIdentifier
  }

  override fun isLiteralExpression(element: PsiElement): Boolean {
    return element is PsiLiteralExpression
  }
}
