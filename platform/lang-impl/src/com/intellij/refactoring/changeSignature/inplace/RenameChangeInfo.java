// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature.inplace;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.rename.RenameProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RenameChangeInfo implements ChangeInfo {
  private final PsiFile myFile;
  private final int myOffset;
  private final String myOldName;

  public RenameChangeInfo(final PsiNameIdentifierOwner namedElement, final ChangeInfo oldInfo) {
    myOldName = oldInfo instanceof RenameChangeInfo ? ((RenameChangeInfo)oldInfo).getOldName() : namedElement.getName();
    myFile = namedElement.getContainingFile();
    myOffset = namedElement.getTextOffset();
  }

  @Override
  public ParameterInfo @NotNull [] getNewParameters() {
    return new ParameterInfo[0];
  }

  @Override
  public boolean isParameterSetOrOrderChanged() {
    return false;
  }

  @Override
  public boolean isParameterTypesChanged() {
    return false;
  }

  @Override
  public boolean isParameterNamesChanged() {
    return false;
  }

  @Override
  public boolean isGenerateDelegate() {
    return false;
  }

  @Override
  public boolean isNameChanged() {
    return true;
  }

  @Override
  public PsiElement getMethod() {
    return getNamedElement();
  }

  @Override
  public boolean isReturnTypeChanged() {
    return false;
  }

  @Override
  public String getNewName() {
    final PsiNameIdentifierOwner nameOwner = getNamedElement();
    return nameOwner != null ? nameOwner.getName() : null;
  }

  public String getOldName() {
    return myOldName;
  }

  public @Nullable PsiNameIdentifierOwner getNamedElement() {
    return PsiTreeUtil.getParentOfType(myFile.findElementAt(myOffset), PsiNameIdentifierOwner.class);
  }

  public void perform() {
    final PsiNameIdentifierOwner element = getNamedElement();
    if (element != null) {
      final String name = element.getName();
      ApplicationManager.getApplication().runWriteAction(() -> {
        element.setName(myOldName);
      });
      new RenameProcessor(element.getProject(), element, name, false, false).run();
    }
  }

  public @Nullable PsiElement getNameIdentifier() {
    final PsiNameIdentifierOwner namedElement = getNamedElement();
    return namedElement != null ? namedElement.getNameIdentifier() : null;
  }
}
