// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class MoveMembersIntoClassFix extends PsiBasedModCommandAction<PsiImplicitClass> {
  public MoveMembersIntoClassFix(PsiImplicitClass implicitClass) {
    super(implicitClass);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.family.name.move.members.into.class");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiImplicitClass implicitClass) {
    long count = StreamEx.of(implicitClass.getChildren()).select(PsiMember.class)
      .remove(PsiClass.class::isInstance).count();
    if (count == 0) return null;
    return Presentation.of(JavaAnalysisBundle.message("intention.name.move.members.into.class", count));
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiImplicitClass implicitClass) {
    return ModCommand.chooseAction(
      JavaAnalysisBundle.message("chooser.popup.title.select.class.to.move.members.to"),
      ContainerUtil.map(implicitClass.getInnerClasses(), MoveAllMembersToParticularClassAction::new));
  }

  private static class MoveAllMembersToParticularClassAction extends PsiUpdateModCommandAction<PsiClass> {
    private final @NonNls String className;

    private MoveAllMembersToParticularClassAction(PsiClass cls) {
      super(cls);
      className = cls.getName();
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiClass element, @NotNull ModPsiUpdater updater) {
      if (!(element.getContainingClass() instanceof PsiImplicitClass unn)) return;
      PsiMember[] members = PsiTreeUtil.getChildrenOfType(unn, PsiMember.class);
      if (members == null) return;
      List<PsiMember> membersWithoutClasses = Arrays.stream(members).filter(member -> !(member instanceof PsiClass)).toList();
      for (PsiMember member : membersWithoutClasses) {
        PsiElement copyMember = member.copy();
        member.delete();
        element.add(copyMember);
      }
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("intention.family.name.move.members.to", className);
    }
  }
}
