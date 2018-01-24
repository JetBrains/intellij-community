// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename

import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope.projectScope
import com.intellij.psi.search.searches.MethodReferencesSearch

class RenameJavaMethodSearchParameters(method: PsiMethod) : MethodReferencesSearch.SearchParameters(
  method, projectScope(method.project), true
) {

  override fun isDirectSearch(): Boolean = true
}
