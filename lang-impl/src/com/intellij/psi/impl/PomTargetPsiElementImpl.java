/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl;

import com.intellij.ide.IconProvider;
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

/**
 * @author peter
 */
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
      return ((PomRenameableTarget)myTarget).isWritable();
    }
    return false;
  }

  public String getTypeName() {
    throw new UnsupportedOperationException("Method getTypeName is not yet implemented for " + myTarget.getClass().getName() + "; see PomDescriptionProvider");
  }

  public Icon getIcon() {
    for (IconProvider iconProvider : IconProvider.EXTENSION_POINT_NAME.getExtensions()) {
      if (iconProvider instanceof PomIconProvider) {
        final Icon icon = ((PomIconProvider)iconProvider).getIcon(myTarget, 0);
        if (icon != null) {
          return icon;
        }
      }
    }

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
      ((PomRenameableTarget)myTarget).setName(name);
      return this;
    } 
    throw new UnsupportedOperationException("Cannot rename " + myTarget);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PomTargetPsiElementImpl that = (PomTargetPsiElementImpl)o;

    if (!myTarget.equals(that.myTarget)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myTarget.hashCode();
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return equals(another);
  }

  @Override
  public PsiElement getContext() {
    if (myTarget instanceof PsiTarget) {
      return ((PsiTarget)myTarget).getNavigationElement();
    }
    return null;
  }

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
  @Nullable
  public PsiFile getContainingFile() {
    if (myTarget instanceof PsiTarget) {
      return ((PsiTarget)myTarget).getNavigationElement().getContainingFile();
    }
    return null;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }
}
