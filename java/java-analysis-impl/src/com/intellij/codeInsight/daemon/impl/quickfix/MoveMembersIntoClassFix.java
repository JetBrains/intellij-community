// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class MoveMembersIntoClassFix implements ModCommandAction {
  private final SmartPsiElementPointer<PsiImplicitClass> myImplicitClass;

  public MoveMembersIntoClassFix(PsiImplicitClass implicitClass) {
    myImplicitClass = SmartPointerManager.createPointer(implicitClass);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.family.name.move.members.into.class");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    return Presentation.of(JavaAnalysisBundle.message("intention.family.name.move.members.into.class"));
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiImplicitClass implicitClass = myImplicitClass.getElement();
    if (implicitClass == null) return ModCommand.nop();
    PsiClass[] innerClasses = implicitClass.getInnerClasses();

    List<? extends ModCommandAction> actionsPerClass = Arrays.stream(innerClasses).map(MoveAllMembersToParticularClassAction::new).toList();

    return ModCommand.chooseAction(JavaAnalysisBundle.message("chooser.popup.title.select.class.to.move.members.to"), actionsPerClass);
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
