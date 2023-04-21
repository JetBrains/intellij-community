// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiVariable
import org.jetbrains.annotations.Nls

class ExtractMultipleVariablesException(val variables: List<PsiVariable>, val scope: List<PsiElement>): ExtractException(errorMessage(), variables)

private fun errorMessage(): @Nls String = JavaRefactoringBundle.message("extract.method.error.many.outputs")