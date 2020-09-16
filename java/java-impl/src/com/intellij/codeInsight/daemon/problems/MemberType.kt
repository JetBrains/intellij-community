// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.psi.*

internal enum class MemberType {
  METHOD,
  CLASS,
  FIELD,
  ENUM_CONSTANT;

  companion object {
    internal fun create(psiMember: PsiMember): MemberType? =
      when (psiMember) {
        is PsiMethod -> METHOD
        is PsiClass -> CLASS
        is PsiField -> FIELD
        is PsiEnumConstant -> ENUM_CONSTANT
        else -> null
      }

    internal fun create(member: Member): MemberType =
      when (member) {
        is Member.Method -> METHOD
        is Member.Class -> CLASS
        is Member.Field -> FIELD
        is Member.EnumConstant -> ENUM_CONSTANT
      }

  }
}