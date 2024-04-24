// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiExpressionCodeFragmentImpl extends PsiCodeFragmentImpl implements PsiExpressionCodeFragment {
  private static final Logger LOG = Logger.getInstance(PsiExpressionCodeFragmentImpl.class);
  private PsiType myExpectedType;

  public PsiExpressionCodeFragmentImpl(@NotNull Project project,
                                       boolean isPhysical,
                                       @NonNls String name,
                                       @NotNull CharSequence text,
                                       final @Nullable PsiType expectedType,
                                       @Nullable PsiElement context) {
    super(project, JavaElementType.EXPRESSION_TEXT, isPhysical, name, text, context);
    setExpectedType(expectedType);
  }

  @Override
  public @Nullable PsiExpression getExpression() {
    ASTNode exprChild = calcTreeElement().findChildByType(ElementType.EXPRESSION_BIT_SET);
    if (exprChild == null) return null;
    return (PsiExpression)SourceTreeToPsiMap.treeElementToPsi(exprChild);
  }

  @Override
  public PsiType getExpectedType() {
    PsiType type = myExpectedType;
    if (type != null && !type.isValid()) {
      return null;
    }
    return type;
  }

  @Override
  public void setExpectedType(@Nullable PsiType type) {
    myExpectedType = type;
    if (type != null) {
      LOG.assertTrue(type.isValid());
    }
  }
}
