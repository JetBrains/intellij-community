// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems.pass;

import com.intellij.navigation.ItemPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.usages.PsiElementUsageTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.util.ObjectUtils.tryCast;

class RelatedProblemTargetAdapter implements PsiElementUsageTarget, ItemPresentation {

  private final SmartPsiElementPointer<PsiElement> myPointer;
  private final String myMemberText;
  private final String myMemberLocation;
  private final Icon myMemberIcon;

  RelatedProblemTargetAdapter(@NotNull PsiElement element,
                              @Nullable String memberText,
                              @Nullable String memberLocation,
                              @Nullable Icon memberIcon) {
    myPointer = SmartPointerManager.createPointer(element);
    myMemberText = memberText;
    myMemberLocation = memberLocation;
    myMemberIcon = memberIcon;
  }

  @Override
  public PsiElement getElement() {
    return myPointer.getElement();
  }

  @Override
  public boolean isValid() {
    return getElement() != null;
  }

  @Override
  public void findUsages() {
  }

  @Override
  public @Nullable String getName() {
    PsiNamedElement namedElement = tryCast(getElement(), PsiNamedElement.class);
    if (namedElement == null) return null;
    return namedElement.getName();
  }

  @Override
  public @Nullable ItemPresentation getPresentation() {
    return this;
  }

  @Override
  public void navigate(boolean requestFocus) {
    Navigatable navigatable = tryCast(getElement(), Navigatable.class);
    if (navigatable != null && navigatable.canNavigate()) navigatable.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    Navigatable navigatable = tryCast(getElement(), Navigatable.class);
    return navigatable != null && navigatable.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    Navigatable navigatable = tryCast(getElement(), Navigatable.class);
    return navigatable != null && navigatable.canNavigateToSource();
  }

  @Override
  public @Nullable String getPresentableText() {
    return myMemberText;
  }

  @Override
  public @Nullable String getLocationString() {
    return myMemberLocation;
  }

  @Override
  public @Nullable Icon getIcon(boolean unused) {
    return myMemberIcon;
  }
}
