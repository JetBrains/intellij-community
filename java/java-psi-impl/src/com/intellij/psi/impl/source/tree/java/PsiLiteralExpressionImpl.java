// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.java.stubs.impl.PsiLiteralStub;
import com.intellij.psi.impl.source.JavaStubPsiElement;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.injected.StringLiteralEscaper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.util.text.LiteralFormatUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class PsiLiteralExpressionImpl
  extends JavaStubPsiElement<PsiLiteralStub>
       implements PsiLiteralExpression, PsiLanguageInjectionHost, ContributedReferenceHost {
  @NonNls private static final String QUOT = "&quot;";

  public static final TokenSet INTEGER_LITERALS = TokenSet.create(JavaTokenType.INTEGER_LITERAL, JavaTokenType.LONG_LITERAL);
  public static final TokenSet REAL_LITERALS = TokenSet.create(JavaTokenType.FLOAT_LITERAL, JavaTokenType.DOUBLE_LITERAL);
  public static final TokenSet NUMERIC_LITERALS = TokenSet.orSet(INTEGER_LITERALS, REAL_LITERALS);

  public PsiLiteralExpressionImpl(@NotNull PsiLiteralStub stub) {
    super(stub, JavaStubElementTypes.LITERAL_EXPRESSION);
  }

  public PsiLiteralExpressionImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
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
    if (type == JavaTokenType.STRING_LITERAL || type == JavaTokenType.RAW_STRING_LITERAL) {
      PsiManagerEx manager = getManager();
      GlobalSearchScope resolveScope = ResolveScopeManager.getElementResolveScope(this);
      return PsiType.getJavaLangString(manager, resolveScope);
    }
    if (type == JavaTokenType.TRUE_KEYWORD || type == JavaTokenType.FALSE_KEYWORD) {
      return PsiType.BOOLEAN;
    }
    if (type == JavaTokenType.NULL_KEYWORD) {
      return PsiType.NULL;
    }
    return null;
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
      String innerText = getInnerText();
      return innerText == null ? null : internedParseStringCharacters(innerText);
    }

    if (type == JavaTokenType.RAW_STRING_LITERAL) {
      return getRawString();
    }

    String text = NUMERIC_LITERALS.contains(type) ? getCanonicalText().toLowerCase(Locale.ENGLISH) : getCanonicalText();
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
      if (StringUtil.endsWithChar(text, '\'')) {
        if (textLength == 1) return null;
        text = text.substring(1, textLength - 1);
      }
      else {
        text = text.substring(1, textLength);
      }
      StringBuilder chars = new StringBuilder();
      boolean success = parseStringCharacters(text, chars, null);
      if (!success) return null;
      if (chars.length() != 1) return null;
      return Character.valueOf(chars.charAt(0));
    }

    return null;
  }

  @Nullable
  public String getInnerText() {
    String text = getCanonicalText();
    int textLength = text.length();
    if (StringUtil.endsWithChar(text, '\"')) {
      if (textLength == 1) return null;
      text = text.substring(1, textLength - 1);
    }
    else {
      if (text.startsWith(QUOT) && text.endsWith(QUOT) && textLength > QUOT.length()) {
        text = text.substring(QUOT.length(), textLength - QUOT.length());
      }
      else {
        return null;
      }
    }
    return text;
  }

  public String getRawString() {
    return StringUtil.nullize(StringUtil.trimLeading(StringUtil.trimTrailing(getCanonicalText(), '`'), '`'));
  }

  @Nullable
  private static String internedParseStringCharacters(final String chars) {
    final StringBuilder outChars = new StringBuilder(chars.length());
    final boolean success = parseStringCharacters(chars, outChars, null);
    return success ? outChars.toString() : null;
  }

  public static boolean parseStringCharacters(@NotNull String chars, @NotNull StringBuilder outChars, @Nullable int[] sourceOffsets) {
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
    IElementType elementType = getLiteralElementType();
    return elementType == JavaTokenType.STRING_LITERAL || elementType == JavaTokenType.RAW_STRING_LITERAL;
  }

  @Override
  @NotNull
  public PsiReference[] getReferences() {
    IElementType type = getLiteralElementType();
    if (type != JavaTokenType.STRING_LITERAL && type != JavaTokenType.RAW_STRING_LITERAL && type != JavaTokenType.INTEGER_LITERAL) {
      return PsiReference.EMPTY_ARRAY; // there are references in int literals in SQL API parameters
    }
    return PsiReferenceService.getService().getContributedReferences(this);
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull final String text) {
    ASTNode valueNode = getNode().getFirstChildNode();
    assert valueNode instanceof LeafElement;
    ((LeafElement)valueNode).replaceWithText(text);
    return this;
  }

  @Override
  @NotNull
  public LiteralTextEscaper<PsiLiteralExpressionImpl> createLiteralTextEscaper() {
    if (getLiteralElementType() == JavaTokenType.RAW_STRING_LITERAL) {
      return new LiteralTextEscaper<PsiLiteralExpressionImpl>(this) {
        @Override
        public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
          outChars.append(rangeInsideHost.substring(myHost.getText()));
          return true;
        }

        @Override
        public int getOffsetInHost(int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
          return offsetInDecoded + rangeInsideHost.getStartOffset();
        }

        @Override
        public boolean isOneLine() {
          return false;
        }
      };
    }
    return new StringLiteralEscaper<>(this);
  }
}
