// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.factories;

import com.intellij.lang.LighterAST;
import com.intellij.lang.LighterASTNode;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiField;
import com.intellij.psi.impl.cache.RecordUtil;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.impl.java.stubs.JavaStubElementType;
import com.intellij.psi.impl.java.stubs.PsiFieldStub;
import com.intellij.psi.impl.java.stubs.impl.PsiFieldStubImpl;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.LightTreeUtil;
import com.intellij.psi.stubs.LightStubElementFactory;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaFieldStubFactory implements LightStubElementFactory<PsiFieldStubImpl, PsiField> {
  private static final int INITIALIZER_LENGTH_LIMIT = 1000;
  
  @Override
  public @NotNull PsiFieldStubImpl createStub(@NotNull LighterAST tree, @NotNull LighterASTNode node, @NotNull StubElement<?> parentStub) {
    final TypeInfo typeInfo = TypeInfo.create(tree, node, parentStub);

    boolean isDeprecatedByComment = false;
    boolean hasDeprecatedAnnotation = false;
    boolean hasDocComment = false;
    String name = null;
    String initializer = null;

    boolean expectingInit = false;
    for (final LighterASTNode child : tree.getChildren(node)) {
      final IElementType type = child.getTokenType();
      if (type == JavaDocElementType.DOC_COMMENT) {
        hasDocComment = true;
        isDeprecatedByComment = RecordUtil.isDeprecatedByDocComment(tree, child);
      }
      else if (type == JavaElementType.MODIFIER_LIST) {
        hasDeprecatedAnnotation = RecordUtil.isDeprecatedByAnnotation(tree, child);
      }
      else if (type == JavaTokenType.IDENTIFIER) {
        name = RecordUtil.intern(tree.getCharTable(), child);
      }
      else if (type == JavaTokenType.EQ) {
        expectingInit = true;
      }
      else if (expectingInit && !ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains(type) && type != JavaTokenType.SEMICOLON) {
        initializer = encodeInitializer(tree, child);
        break;
      }
    }

    final boolean isEnumConst = node.getTokenType() == JavaElementType.ENUM_CONSTANT;
    final byte flags = PsiFieldStubImpl.packFlags(isEnumConst, isDeprecatedByComment, hasDeprecatedAnnotation, hasDocComment);

    return new PsiFieldStubImpl(parentStub, name, typeInfo, initializer, flags);
  }

  @Override
  public PsiField createPsi(@NotNull PsiFieldStubImpl stub) {
    return JavaStubElementType.getFileStub(stub).getPsiFactory().createField(stub);
  }
  
  @Override
  public @NotNull PsiFieldStubImpl createStub(@NotNull PsiField psi, @Nullable StubElement parentStub) {
    final String message =
      "Should not be called. Element=" + psi + "; class" + psi.getClass() + "; file=" + (psi.isValid() ? psi.getContainingFile() : "-");
    throw new UnsupportedOperationException(message);
  }
  
  private static String encodeInitializer(final LighterAST tree, final LighterASTNode initializer) {
    final IElementType type = initializer.getTokenType();
    if (type == JavaElementType.NEW_EXPRESSION || type == JavaElementType.METHOD_CALL_EXPRESSION) {
      return PsiFieldStub.INITIALIZER_NOT_STORED;
    }

    if (initializer.getEndOffset() - initializer.getStartOffset() > INITIALIZER_LENGTH_LIMIT) {
      return PsiFieldStub.INITIALIZER_TOO_LONG;
    }

    return LightTreeUtil.toFilteredString(tree, initializer, null);
  }
}