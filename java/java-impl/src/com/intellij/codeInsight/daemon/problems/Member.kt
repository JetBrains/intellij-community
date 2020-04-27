// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.psi.PsiMember
import com.intellij.psi.search.GlobalSearchScope

internal data class Member(val psiMember: PsiMember, val name: String, val scope: GlobalSearchScope) {
  companion object {
    internal fun create(psiMember: PsiMember, scope: GlobalSearchScope): Member? {
      val name = psiMember.name ?: return null
      return Member(psiMember, name, scope)
    }
  }
}