// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.compiled;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.ui.icons.RowIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ClsClassObjectAccessExpressionImpl extends ClsElementImpl implements PsiClassObjectAccessExpression {
  private final ClsElementImpl myParent;
  private final ClsTypeElementImpl myTypeElement;

  public ClsClassObjectAccessExpressionImpl(ClsElementImpl parent, String canonicalClassText) {
    myParent = parent;
    myTypeElement = new ClsTypeElementImpl(this, canonicalClassText, ClsTypeElementImpl.VARIANCE_NONE);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    myTypeElement.appendMirrorText(0, buffer);
    buffer.append('.').append(JavaKeywords.CLASS);
  }

  @Override
  protected void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
    setMirrorCheckingType(element, null);
    setMirror(getOperand(), SourceTreeToPsiMap.<PsiClassObjectAccessExpression>treeToPsiNotNull(element).getOperand());
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return new PsiElement[]{myTypeElement};
  }

  @Override
  public PsiElement getParent() {
    return myParent;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitClassObjectAccessExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiTypeElement getOperand() {
    return myTypeElement;
  }

  @Override
  public @NotNull PsiType getType() {
    return PsiImplUtil.getType(this);
  }

  @Override
  public String getText() {
    final StringBuilder buffer = new StringBuilder();
    appendMirrorText(0, buffer);
    return buffer.toString();
  }

  @Override
  public Icon getElementIcon(final int flags) {
    IconManager iconManager = IconManager.getInstance();
    RowIcon rowIcon = iconManager.createLayeredIcon(this, iconManager.getPlatformIcon(PlatformIcons.Field), 0);
    rowIcon.setIcon(iconManager.getPlatformIcon(PlatformIcons.Public), 1);
    return rowIcon;
  }
}
