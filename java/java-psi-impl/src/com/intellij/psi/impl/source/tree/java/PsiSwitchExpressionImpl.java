// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import org.jetbrains.annotations.NotNull;

public class PsiSwitchExpressionImpl extends PsiSwitchBlockImpl implements PsiSwitchExpression {
  public PsiSwitchExpressionImpl() {
    super(JavaElementType.SWITCH_EXPRESSION);
  }

  @Override
  public PsiExpression getExpression() {
    return (PsiExpression)findPsiChildByType(ElementType.EXPRESSION_BIT_SET);
  }

  @Override
  public PsiType getType() {
    //todo[ann] http://cr.openjdk.java.net/~gbierman/switch-expressions.html#jep325-15.29.1
    PsiClass objClass = JavaPsiFacade.getInstance(getProject()).findClass(CommonClassNames.JAVA_LANG_OBJECT, getResolveScope());
    return objClass != null ? new PsiImmediateClassType(objClass, PsiSubstitutor.EMPTY) : null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSwitchExpression(this);
    }
    else {
      super.accept(visitor);
    }
  }

  @Override
  public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
    super.replaceChildInternal(child, JavaSourceUtil.addParenthToReplacedChild(child, newElement, getManager()));
  }

  @Override
  public String toString() {
    PsiExpression expression = getExpression();
    return "PsiSwitchExpression: " + (expression != null ? expression.getText() : "(incomplete)");
  }
}