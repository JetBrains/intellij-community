// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.refactoring.classMembers.MemberInfoModel;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.IconUtil;
import com.intellij.util.VisibilityIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class MemberSelectionTable extends AbstractMemberSelectionTable<PsiMember, MemberInfo> {

  public MemberSelectionTable(final List<MemberInfo> memberInfos, @NlsContexts.ColumnName String abstractColumnHeader) {
    this(memberInfos, null, abstractColumnHeader);
  }

  public MemberSelectionTable(final List<MemberInfo> memberInfos, MemberInfoModel<PsiMember, MemberInfo> memberInfoModel, @NlsContexts.ColumnName String abstractColumnHeader) {
    super(memberInfos, memberInfoModel, abstractColumnHeader);
  }

  @Override
  protected @Nullable Object getAbstractColumnValue(MemberInfo memberInfo) {
    if (!(memberInfo.getMember() instanceof PsiMethod method)) return null;
    if (memberInfo.isStatic()) return null;

    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final Boolean fixedAbstract = myMemberInfoModel.isFixedAbstract(memberInfo);
      if (fixedAbstract != null) return fixedAbstract;
    }

    if (!myMemberInfoModel.isAbstractEnabled(memberInfo)) {
      return myMemberInfoModel.isAbstractWhenDisabled(memberInfo);
    }
    else {
      return memberInfo.isToAbstract();
    }
  }

  @Override
  protected boolean isAbstractColumnEditable(int rowIndex) {
    MemberInfo info = myMemberInfos.get(rowIndex);
    if (!(info.getMember() instanceof PsiMethod method)) return false;
    if (info.isStatic()) return false;

    if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      if (myMemberInfoModel.isFixedAbstract(info) != null) {
        return false;
      }
    }

    return info.isChecked() && myMemberInfoModel.isAbstractEnabled(info);
  }

  @Override
  protected void setVisibilityIcon(MemberInfo memberInfo, com.intellij.ui.RowIcon icon) {
    setVisibilityIcon(memberInfo, (RowIcon)icon);
  }

  @Override
  protected void setVisibilityIcon(MemberInfo memberInfo, RowIcon icon) {
    PsiMember member = memberInfo.getMember();
    PsiModifierList modifiers = member != null ? member.getModifierList() : null;
    if (modifiers != null) {
      VisibilityIcons.setVisibilityIcon(modifiers, icon);
    }
    else {
      icon.setIcon(IconUtil.getEmptyIcon(true), VISIBILITY_ICON_POSITION);
    }
  }

  @Override
  protected Icon getOverrideIcon(MemberInfo memberInfo) {
    PsiMember member = memberInfo.getMember();
    Icon overrideIcon = AbstractMemberSelectionTable.EMPTY_OVERRIDE_ICON;
    if (member instanceof PsiMethod) {
      if (Boolean.TRUE.equals(memberInfo.getOverrides())) {
        overrideIcon = AllIcons.General.OverridingMethod;
      }
      else if (Boolean.FALSE.equals(memberInfo.getOverrides())) {
        overrideIcon = AllIcons.General.ImplementingMethod;
      }
      else {
        overrideIcon = AbstractMemberSelectionTable.EMPTY_OVERRIDE_ICON;
      }
    }
    return overrideIcon;
  }
}
