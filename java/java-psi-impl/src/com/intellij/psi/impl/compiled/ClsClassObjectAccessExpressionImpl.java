// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.ui.IconManager;
import com.intellij.ui.icons.RowIcon;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author ven
 */
public class ClsClassObjectAccessExpressionImpl extends ClsElementImpl implements PsiClassObjectAccessExpression {
  private final ClsElementImpl myParent;
  private final ClsTypeElementImpl myTypeElement;

  public ClsClassObjectAccessExpressionImpl(ClsElementImpl parent, String canonicalClassText) {
    myParent = parent;
    myTypeElement = new ClsTypeElementImpl(this, canonicalClassText, ClsTypeElementImpl.VARIANCE_NONE);
  }

  @Override
  public void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer) {
    myTypeElement.appendMirrorText(0, buffer);
    buffer.append('.').append(PsiKeyword.CLASS);
  }

  @Override
  public void setMirror(@NotNull TreeElement element) throws InvalidMirrorException {
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
  @NotNull
  public PsiTypeElement getOperand() {
    return myTypeElement;
  }

  @NotNull
  @Override
  public PsiType getType() {
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
    RowIcon rowIcon = IconManager.getInstance().createLayeredIcon(this, PlatformIcons.FIELD_ICON, 0);
    rowIcon.setIcon(PlatformIcons.PUBLIC_ICON, 1);
    return rowIcon;
  }
}
