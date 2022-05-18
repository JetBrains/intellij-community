// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.tree.JavaElementType.RECORD_PATTERN;

public class PsiRecordPatternImpl extends CompositePsiElement implements PsiRecordPattern {
  public PsiRecordPatternImpl() {
    super(RECORD_PATTERN);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitRecordPattern(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiRecordStructurePattern getStructurePattern() {
    PsiRecordStructurePattern recordStructurePattern =
      (PsiRecordStructurePattern)findPsiChildByType(JavaElementType.RECORD_STRUCTURE_PATTERN);
    assert recordStructurePattern != null; // guaranteed by parser
    return recordStructurePattern;
  }

  @Override
  public String getName() {
    PsiElement identifier = getNameIdentifier();
    return identifier == null
           ? null
           : identifier.getText();
  }

  @Nullable
  private PsiElement getNameIdentifier() {
    return findPsiChildByType(JavaTokenType.IDENTIFIER);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiElement identifier = getNameIdentifier();
    if (identifier == null) {
      PsiRecordStructurePattern structurePattern = getStructurePattern();
      if (!PsiUtil.isJavaToken(structurePattern.getLastChild(), JavaTokenType.RPARENTH)) {
        // can't put identifier - after reparse it will be part of the PsiRecordStructurePattern
        throw new IncorrectOperationException();
      }
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(getManager().getProject());
      PsiIdentifier newNameIdentifier = factory.createIdentifier(name);
      addAfter(newNameIdentifier, structurePattern);
    }
    else {
      PsiImplUtil.setName(identifier, name);
    }
    return this;
  }

  @Override
  public String toString() {
    String name = getName();
    if (name == null) {
      return "PsiRecordPattern";
    }
    return "PsiRecordPattern " + name;
  }
}
