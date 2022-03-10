// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.impl.PsiLiteralStub;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.injected.StringLiteralEscaper;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.util.text.LiteralFormatUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiLiteralExpressionImpl
  extends JavaStubPsiElement<PsiLiteralStub>
  implements PsiLiteralExpression, PsiLanguageInjectionHost, ContributedReferenceHost {

  private static final TokenSet NUMERIC_LITERALS = TokenSet.orSet(ElementType.INTEGER_LITERALS, ElementType.REAL_LITERALS);

  public PsiLiteralExpressionImpl(@NotNull PsiLiteralStub stub) {
    super(stub, JavaStubElementTypes.LITERAL_EXPRESSION);
  }

  public PsiLiteralExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return ((CompositeElement)getNode()).getChildrenAsPsiElements((TokenSet)null, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiType getType() {
    final IElementType type = getLiteralElementType();
    if (type == JavaTokenType.INTEGER_LITERAL) {
      return PsiType.INT;
    }
    if (type == JavaTokenType.LONG_LITERAL) {
      return PsiType.LONG;
    }
    if (type == JavaTokenType.FLOAT_LITERAL) {
      return PsiType.FLOAT;
    }
    if (type == JavaTokenType.DOUBLE_LITERAL) {
      return PsiType.DOUBLE;
    }
    if (type == JavaTokenType.CHARACTER_LITERAL) {
      return PsiType.CHAR;
    }
    if (ElementType.STRING_LITERALS.contains(type)) {
      PsiFile file = getContainingFile();
      return PsiType.getJavaLangString(file.getManager(), ResolveScopeManager.getElementResolveScope(file));
    }
    if (type == JavaTokenType.TRUE_KEYWORD || type == JavaTokenType.FALSE_KEYWORD) {
      return PsiType.BOOLEAN;
    }
    if (type == JavaTokenType.NULL_KEYWORD) {
      return PsiType.NULL;
    }
    return null;
  }

  @Override
  public boolean isTextBlock() {
    return getLiteralElementType() == JavaTokenType.TEXT_BLOCK_LITERAL;
  }

  public IElementType getLiteralElementType() {
    PsiLiteralStub stub = getGreenStub();
    if (stub != null) return stub.getLiteralType();

    return getNode().getFirstChildNode().getElementType();
  }

  public String getCanonicalText() {
    IElementType type = getLiteralElementType();
    return NUMERIC_LITERALS.contains(type) ? LiteralFormatUtil.removeUnderscores(getText()) : getText();
  }

  @Override
  public String getText() {
    PsiLiteralStub stub = getGreenStub();
    if (stub != null) return stub.getLiteralText();

    return super.getText();
  }

  @Override
  public Object getValue() {
    final IElementType type = getLiteralElementType();
    if (type == JavaTokenType.TRUE_KEYWORD) {
      return Boolean.TRUE;
    }
    if (type == JavaTokenType.FALSE_KEYWORD) {
      return Boolean.FALSE;
    }

    if (type == JavaTokenType.STRING_LITERAL) {
      return internedParseStringCharacters(PsiLiteralUtil.getStringLiteralContent(this));
    }
    if (type == JavaTokenType.TEXT_BLOCK_LITERAL) {
      return internedParseStringCharacters(PsiLiteralUtil.getTextBlockText(this));
    }

    String text = NUMERIC_LITERALS.contains(type) ? StringUtil.toLowerCase(getCanonicalText()) : getCanonicalText();
    final int textLength = text.length();

    if (type == JavaTokenType.INTEGER_LITERAL) {
      return PsiLiteralUtil.parseInteger(text);
    }
    if (type == JavaTokenType.LONG_LITERAL) {
      return PsiLiteralUtil.parseLong(text);
    }
    if (type == JavaTokenType.FLOAT_LITERAL) {
      return PsiLiteralUtil.parseFloat(text);
    }
    if (type == JavaTokenType.DOUBLE_LITERAL) {
      return PsiLiteralUtil.parseDouble(text);
    }

    if (type == JavaTokenType.CHARACTER_LITERAL) {
      if (textLength == 1 || !StringUtil.endsWithChar(text, '\'')) {
        return null;
      }
      text = text.substring(1, textLength - 1);
      StringBuilder chars = new StringBuilder();
      boolean success = parseStringCharacters(text, chars, null);
      if (!success) return null;
      if (chars.length() != 1) return null;
      return chars.charAt(0);
    }

    return null;
  }

  /**
   * @deprecated use {@link PsiLiteralUtil#getStringLiteralContent(PsiLiteralExpression)} instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  @Nullable
  public String getInnerText() {
    return PsiLiteralUtil.getStringLiteralContent(this);
  }

  @Nullable
  private static String internedParseStringCharacters(final String chars) {
    if (chars == null) return null;
    final StringBuilder outChars = new StringBuilder(chars.length());
    final boolean success = parseStringCharacters(chars, outChars, null);
    return success ? outChars.toString() : null;
  }

  public static boolean parseStringCharacters(@NotNull String chars, @NotNull StringBuilder outChars, int @Nullable [] sourceOffsets) {
    return CodeInsightUtilCore.parseStringCharacters(chars, outChars, sourceOffsets);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitLiteralExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiLiteralExpression:" + getText();
  }

  @Override
  public boolean isValidHost() {
    return ElementType.TEXT_LITERALS.contains(getLiteralElementType());
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    IElementType type = getLiteralElementType();
    return ElementType.STRING_LITERALS.contains(type) || type == JavaTokenType.INTEGER_LITERAL  // int literals could refer to SQL parameters
           ? PsiReferenceService.getService().getContributedReferences(this)
           : PsiReference.EMPTY_ARRAY;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull final String text) {
    ASTNode valueNode = getNode().getFirstChildNode();
    assert valueNode instanceof LeafElement;
    ((LeafElement)valueNode).replaceWithText(text);
    return this;
  }

  @NotNull
  @Override
  public LiteralTextEscaper<PsiLiteralExpressionImpl> createLiteralTextEscaper() {
    return new StringLiteralEscaper<>(this);
  }
}