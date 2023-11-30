// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ift.lesson.navigation

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl
import com.intellij.psi.util.parentOfType
import training.dsl.LessonContext
import training.learn.lesson.general.navigation.DeclarationAndUsagesLesson

class JavaDeclarationAndUsagesLesson : DeclarationAndUsagesLesson() {
  override fun LessonContext.setInitialPosition(): Unit = caret("foo()")
  override val sampleFilePath: String get() = "src/DerivedClass2.java"
  override val entityName: String = "foo"

  override fun getParentExpression(element: PsiElement): PsiElement? {
    return element.parentOfType<PsiReferenceExpressionImpl>(false)
  }
}
