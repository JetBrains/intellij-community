/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.compiled;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ClsPrefixExpressionImpl extends ClsElementImpl implements PsiPrefixExpression {
  private final ClsElementImpl myParent;
  private final PsiExpression myOperand;

  private final MySign mySign = new MySign();

  public ClsPrefixExpressionImpl(ClsElementImpl parent, PsiExpression operand) {
    myParent = parent;
    myOperand = operand;
  }

  public PsiExpression getOperand() {
    return myOperand;
  }

  @NotNull
  public PsiJavaToken getOperationSign() {
    return mySign;
  }

  @NotNull
  public IElementType getOperationTokenType() {
    return getOperationSign().getTokenType();
  }

  public PsiType getType() {
    return myOperand.getType();
  }

  public PsiElement getParent() {
    return myParent;
  }

  @NotNull
  public PsiElement[] getChildren() {
    return new PsiElement[]{getOperationSign(), getOperand()};
  }

  public String getText() {
    return "-" + myOperand.getText();
  }

  public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
    buffer.append(getText());
  }

  public void setMirror(@NotNull TreeElement element) {
    setMirrorCheckingType(element, JavaElementType.PREFIX_EXPRESSION);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPrefixExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiPrefixExpression:" + getText();
  }

  private class MySign extends ClsElementImpl implements PsiJavaToken {
    public IElementType getTokenType() {
      return JavaTokenType.MINUS;
    }

    @NotNull
    public PsiElement[] getChildren() {
      return EMPTY_ARRAY;
    }

    public PsiElement getParent() {
      return ClsPrefixExpressionImpl.this;
    }

    public void appendMirrorText(final int indentLevel, final StringBuilder buffer) {
      buffer.append("-");
    }

    public void setMirror(@NotNull TreeElement element) {
      setMirrorCheckingType(element, JavaTokenType.MINUS);
    }

    public void accept(@NotNull PsiElementVisitor visitor) {
      if (visitor instanceof JavaElementVisitor) {
        ((JavaElementVisitor)visitor).visitJavaToken(this);
      }
      else {
        visitor.visitElement(this);
      }
    }
  }
}
