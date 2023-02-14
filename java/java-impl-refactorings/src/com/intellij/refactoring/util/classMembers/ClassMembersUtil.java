// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util.classMembers;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.classMembers.MemberInfoBase;

public final class ClassMembersUtil {
  public static boolean isProperMember(MemberInfoBase memberInfo) {
    final PsiElement member = memberInfo.getMember();
    return member instanceof PsiField || member instanceof PsiMethod
                || (member instanceof PsiClass && memberInfo.getOverrides() == null);
  }

  public static boolean isImplementedInterface(MemberInfoBase memberInfo) {
    return memberInfo.getMember() instanceof PsiClass && Boolean.FALSE.equals(memberInfo.getOverrides());
  }
}
