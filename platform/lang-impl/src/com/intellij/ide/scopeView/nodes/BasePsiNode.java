// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.scopeView.nodes;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class BasePsiNode<T extends PsiElement> extends PackageDependenciesNode {
  private final SmartPsiElementPointer myPsiElementPointer;
  private Icon myIcon;

  public BasePsiNode(final T element) {
    super(element.getProject());
    if (element.isValid()) {
      myPsiElementPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(element);
    }
    else {
      myPsiElementPointer = null;
    }
  }

  @Override
  public @Nullable PsiElement getPsiElement() {
    if (myPsiElementPointer == null) return null;
    final PsiElement element = myPsiElementPointer.getElement();
    return element != null && element.isValid() ? element : null;
  }

  @Override
  public Icon getIcon() {
    final PsiElement element = getPsiElement();
    if (myIcon == null) {
      myIcon = element != null && element.isValid() ? element.getIcon(Iconable.ICON_FLAG_VISIBILITY | Iconable.ICON_FLAG_READ_STATUS) : null;
    }
    return myIcon;
  }

  @Override
  public @Nullable Color getColor() {
    if (myColor == null && getContainingFile() != null) {
      myColor = FileStatusManager.getInstance(myProject).getStatus(myPsiElementPointer.getVirtualFile()).getColor();
      if (myColor == null) {
        myColor = NOT_CHANGED;
      }
    }
    return myColor == NOT_CHANGED ? null : myColor;
  }

  @Override
  public int getWeight() {
    return 4;
  }

  @Override
  public int getContainingFiles() {
    return 0;
  }

  public boolean equals(Object o) {
    if (isEquals()){
      return super.equals(o);
    }
    if (this == o) return true;
    if (!(o instanceof BasePsiNode methodNode)) return false;

    if (!Comparing.equal(getPsiElement(), methodNode.getPsiElement())) return false;

    return true;
  }

  public int hashCode() {
    PsiElement psiElement = getPsiElement();
    return psiElement == null ? 0 : psiElement.hashCode();
  }

  public PsiFile getContainingFile() {
    return myPsiElementPointer.getContainingFile();
  }

  @Override
  public boolean isValid() {
    final PsiElement element = getPsiElement();
    return element != null && element.isValid();
  }

  public boolean isDeprecated() {
    return false;
  }

}
