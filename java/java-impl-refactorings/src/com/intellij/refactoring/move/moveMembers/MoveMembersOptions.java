// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Nullable
  default String getExplicitMemberVisibility() {
    String visibility = getMemberVisibility();
    if (VisibilityUtil.ESCALATE_VISIBILITY.equals(visibility)) {
      return PsiModifier.PUBLIC;
    }
    return visibility;
  }

  boolean makeEnumConstant();
}
