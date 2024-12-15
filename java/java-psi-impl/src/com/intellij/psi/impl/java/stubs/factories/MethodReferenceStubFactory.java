// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.impl.java.stubs.FunctionalExpressionStub;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.impl.source.tree.java.PsiMethodReferenceExpressionImpl;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodReferenceStubFactory implements LightStubElementFactory<FunctionalExpressionStub<PsiMethodReferenceExpression>, PsiMethodReferenceExpression> {
  //prevents cyclic static variables initialization
  private static final NotNullLazyValue<TokenSet> EXCLUDE_FROM_PRESENTABLE_TEXT = NotNullLazyValue.lazy(() -> {
    return TokenSet.orSet(ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET, TokenSet.create(JavaElementType.REFERENCE_PARAMETER_LIST));
  });

  @Override
  public @NotNull FunctionalExpressionStub<PsiMethodReferenceExpression> createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    return new FunctionalExpressionStub<>(parentStub, JavaStubElementTypes.METHOD_REF_EXPRESSION, getPresentableText(tree, node));
  }

  @Override
  public PsiMethodReferenceExpression createPsi(@NotNull FunctionalExpressionStub<PsiMethodReferenceExpression> stub) {
    return new PsiMethodReferenceExpressionImpl(stub);
  }
  
  @Override
  public @NotNull FunctionalExpressionStub<PsiMethodReferenceExpression> createStub(@NotNull PsiMethodReferenceExpression psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }

  private static @NotNull String getPresentableText(@NotNull LighterAST tree, @NotNull LighterASTNode funExpr) {
    return LightTreeUtil.toFilteredString(tree, funExpr, EXCLUDE_FROM_PRESENTABLE_TEXT.getValue());
  }
}