// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveMembers;

import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifier;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.Nullable;

public interface MoveMembersOptions {
  PsiMember[] getSelectedMembers();

  String getTargetClassName();

  @PsiModifier.ModifierConstant
  @Nullable
  String getMemberVisibility();

  @PsiModifier.ModifierConstant
  default @Nullable String getExplicitMemberVisibility() {
    String visibility = getMemberVisibility();
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(visibility)) {
      return PsiModifier.PUBLIC;
    }
    return visibility;
  }

  boolean makeEnumConstant();
}
