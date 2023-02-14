// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.ide.IconProvider;
import com.intellij.ide.TypePresentationService;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.pom.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTarget;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PomTargetPsiElementImpl extends RenameableFakePsiElement implements PomTargetPsiElement {
  private final PomTarget myTarget;
  private final Project myProject;

  public PomTargetPsiElementImpl(@NotNull PsiTarget target) {
    this(target.getNavigationElement().getProject(), target);
  }

  public PomTargetPsiElementImpl(@NotNull Project project, @NotNull PomTarget target) {
    super(null);
    myProject = project;
    myTarget = target;
  }

  @Override
  @NotNull
  public PomTarget getTarget() {
    return myTarget;
  }

  @Override
  public String getName() {
    if (myTarget instanceof PomNamedTarget) {
      return ((PomNamedTarget)myTarget).getName();
    }
    return null;
  }

  @Override
  public boolean isWritable() {
    if (myTarget instanceof PomRenameableTarget) {
      return ((PomRenameableTarget<?>)myTarget).isWritable();
    }
    return false;
  }

  @Override
  public String getTypeName() {
    throw new UnsupportedOperationException("Method getTypeName is not yet implemented for " + myTarget.getClass().getName() + "; see PomDescriptionProvider");
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    if (myTarget instanceof PsiTarget) {
      return ((PsiTarget)myTarget).getNavigationElement();
    }
    return super.getNavigationElement();
  }

  @Override
  public Icon getIcon() {
    for (IconProvider iconProvider : IconProvider.EXTENSION_POINT_NAME.getExtensionList()) {
      if (iconProvider instanceof PomIconProvider) {
        Icon icon = ((PomIconProvider)iconProvider).getIcon(myTarget, 0);
        if (icon != null) {
          return icon;
        }
      }
    }

    Icon icon = TypePresentationService.getService().getIcon(myTarget);
    if (icon != null) return icon;

    if (myTarget instanceof PsiTarget) {
      return ((PsiTarget)myTarget).getNavigationElement().getIcon(0);
    }
    return null;
  }

  @Override
  public boolean isValid() {
    if (myTarget instanceof PsiTarget) {
      return ((PsiTarget)myTarget).getNavigationElement().isValid();
    }

    return myTarget.isValid();
  }

  @Override
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    if (myTarget instanceof PomRenameableTarget) {
      ((PomRenameableTarget<?>)myTarget).setName(name);
      return this;
    }
    throw new UnsupportedOperationException("Cannot rename " + myTarget);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PomTargetPsiElementImpl that = (PomTargetPsiElementImpl)o;
    return myTarget.equals(that.myTarget);
  }

  @Override
  public int hashCode() {
    return myTarget.hashCode();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return equals(another) ||
           (another != null && myTarget instanceof PsiTarget && another.isEquivalentTo(((PsiTarget)myTarget).getNavigationElement()));
  }

  @Override
  public PsiElement getContext() {
    if (myTarget instanceof PsiTarget) {
      return ((PsiTarget)myTarget).getNavigationElement();
    }
    return null;
  }

  @Override
  @Nullable
  public PsiElement getParent() {
    return null;
  }

  @Override
  public void navigate(boolean requestFocus) {
    myTarget.navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return myTarget.canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return myTarget.canNavigateToSource();
  }

  @Override
  public @Nullable PsiFile getContainingFile() {
    if (myTarget instanceof PsiTarget) {
      return ((PsiTarget)myTarget).getNavigationElement().getContainingFile();
    }
    return null;
  }

  @Override
  public @NotNull Language getLanguage() {
    if (myTarget instanceof PsiTarget) {
      return ((PsiTarget)myTarget).getNavigationElement().getLanguage();
    }
    return Language.ANY;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public String getLocationString() {
    if (myTarget instanceof PsiTarget) {
      PsiFile file = ((PsiTarget)myTarget).getNavigationElement().getContainingFile();
      if (file != null) {
        return "(" + file.getName() + ")";
      }
    }
    return super.getLocationString();
  }
}
