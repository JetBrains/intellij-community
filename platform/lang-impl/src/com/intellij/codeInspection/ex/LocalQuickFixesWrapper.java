// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LocalQuickFixesWrapper extends QuickFixAction {
  private final List<@NotNull LocalQuickFixWrapper> myFixActions = new ArrayList<>();

  public LocalQuickFixesWrapper(@NlsActions.ActionText String name,
                                @NotNull List<QuickFix<?>> fixes,
                                @NotNull InspectionToolWrapper toolWrapper) {
    super(name, toolWrapper);
    fixes.forEach(f -> addFixAction(f, toolWrapper));
    setText(StringUtil.escapeMnemonics(name));
  }

  public void setText(@NotNull @NlsActions.ActionText String text) {
    getTemplatePresentation().setText(text);
  }

  @Override
  protected boolean isProblemDescriptorsAcceptable() {
    return true;
  }

  @Override
  protected boolean applyFix(RefEntity @NotNull [] refElements) {
    return true;
  }

  @Override
  protected void applyFix(@NotNull final Project project,
                          @NotNull final GlobalInspectionContextImpl context,
                          final CommonProblemDescriptor @NotNull [] descriptors,
                          @NotNull final Set<? super PsiElement> ignoredElements) {
    for (LocalQuickFixWrapper fixAction : myFixActions) {
      fixAction.applyFix(project, context, descriptors, ignoredElements);
    }
  }

  @Override
  protected boolean startInWriteAction() {
    for (LocalQuickFixWrapper fixAction : myFixActions) {
      if (!fixAction.startInWriteAction()) return false;
    }
    return true;
  }

  @Override
  protected void performFixesInBatch(@NotNull Project project,
                                     @NotNull List<CommonProblemDescriptor[]> descriptors,
                                     @NotNull GlobalInspectionContextImpl context,
                                     Set<? super PsiElement> ignoredElements) {
    for (LocalQuickFixWrapper fixAction : myFixActions) {
      fixAction.performFixesInBatch(project, descriptors, context, ignoredElements);
    }
  }

  public void addFixAction(@NotNull QuickFix fix, @NotNull InspectionToolWrapper toolWrapper) {
    for (LocalQuickFixWrapper action : myFixActions) {
      if (action.getFix().getClass() == fix.getClass()) return;
    }
    myFixActions.add(new LocalQuickFixWrapper(fix, toolWrapper));
  }
}