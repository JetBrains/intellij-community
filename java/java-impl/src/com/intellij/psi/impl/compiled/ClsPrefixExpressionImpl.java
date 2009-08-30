package com.intellij.psi.impl.compiled;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class ClsPrefixExpressionImpl extends ClsElementImpl implements PsiPrefixExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsPrefixExpressionImpl");

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

  public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
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

    public void appendMirrorText(final int indentLevel, final StringBuffer buffer) {
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
