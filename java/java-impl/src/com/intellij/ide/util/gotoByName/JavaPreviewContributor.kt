// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName

import com.intellij.ide.actions.searcheverywhere.SearchEverywherePreviewProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

class JavaPreviewContributor : SearchEverywherePreviewProvider {
  override fun getElement(project: Project, psiFile: PsiFile): PsiElement? {
    return PsiTreeUtil.getChildOfType(psiFile, PsiClass::class.java)
  }
}