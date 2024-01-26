// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.impl.source.tree.injected.StringLiteralEscaper;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiLiteralUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class PsiFragmentImpl extends LeafPsiElement implements PsiFragment {
  public static final Key<Integer> FRAGMENT_INDENT_KEY = Key.create("FRAGMENT_INDENT_KEY");

  public PsiFragmentImpl(@NotNull IElementType type, @NotNull CharSequence text) {
    super(type, text);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitFragment(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @Nullable String getValue() {
    final String content = getFragmentContent(this);
    if (content == null) return null;
    final CharSequence sequence = CodeInsightUtilCore.parseStringCharacters(content, null);
    if (sequence == null) return null;
    return sequence.toString();
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    ASTNode valueNode = getNode();
    assert valueNode instanceof LeafElement;
    ((LeafElement)valueNode).replaceWithText(text);
    return this;
  }

  @Override
  public @NotNull LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new StringLiteralEscaper<>(this);
  }

  private static String getFragmentContent(PsiFragment fragment) {
    final IElementType tokenType = fragment.getTokenType();

    if (tokenType == JavaTokenType.STRING_TEMPLATE_BEGIN || tokenType == JavaTokenType.STRING_TEMPLATE_MID) {
      String text = fragment.getText();
      return text.substring(1, text.length() - 2);
    }
    else if (tokenType == JavaTokenType.STRING_TEMPLATE_END) {
      String text = fragment.getText();
      if (!(text.endsWith("\""))) {
        return null;
      }
      return text.substring(1, text.length() - 1);
    }

    return getTextBlockFragmentContent(fragment);
  }

  @Nullable
  private static String getTextBlockFragmentContent(PsiFragment fragment) {
    final IElementType tokenType = fragment.getTokenType();
    final String text = fragment.getText();
    String content;
    if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
      if (!(text.startsWith("\"\"\""))) {
        return null;
      }
      int start = 3;
      while (true) {
        char c = text.charAt(start++);
        if (c == '\n') {
          break;
        }
        if (!PsiLiteralUtil.isTextBlockWhiteSpace(c) || start == text.length()) {
          return null;
        }
      }
      content = text.substring(start, text.length() - 2);
    }
    else if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID) {
      content = text.substring(1, text.length() - 2);
    }
    else if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_END) {
      if (!(text.endsWith("\"\"\""))) return null;
      content = text.substring(1, text.length() - 3);
    }
    else {
      return null;
    }

    int indent = getTextBlockFragmentIndent(fragment);
    return (indent < 0) ? null : stripTextBlockIndent(tokenType, content, indent);
  }

  private static @NotNull String stripTextBlockIndent(IElementType tokenType, String content, int indent) {
    final StringBuilder result = new StringBuilder();
    int strip = (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) ? 0 : -1;
    for (int i = 0, length = content.length(); i < length; i++) {
      final char c = content.charAt(i);
      if (strip >= 0) {
        if (c == '\n') {
          strip = -1;
        }
        else if (strip <= indent) {
          strip++;
        }
      }
      if (c == '\n') {
        while (result.length() > 0) {
          int end = result.length() - 1;
          char d = result.charAt(end);
          if (d == '\n' || !Character.isWhitespace(d)) break;
          // trim line trailing whitespace
          result.deleteCharAt(end);
        }
        strip = 0;
      }
      else if (strip > indent && indent > 0) {
        // strip indent
        int end = result.length();
        result.delete(end - indent, end);
        strip = -1;
      }
      result.append(c);
    }
    return result.toString();
  }

  public static int getTextBlockFragmentIndent(PsiFragment fragment) {
    final PsiElement parent = fragment.getParent();
    if (!(parent instanceof PsiTemplate)) {
      return -1;
    }
    final PsiTemplate template = (PsiTemplate)parent;
    Integer cache = template.getUserData(FRAGMENT_INDENT_KEY);
    if (cache != null) {
      return cache;
    }
    final StringBuilder sb = new StringBuilder();
    for (PsiFragment templateFragment : template.getFragments()) {
      sb.append(templateFragment.getText());
    }
    final String[] lines = PsiLiteralUtil.getTextBlockLines(sb.toString());
    if (lines == null) {
      return -1;
    }
    int indent = PsiLiteralUtil.getTextBlockIndent(lines);
    fragment.putUserData(FRAGMENT_INDENT_KEY, indent);
    return indent;
  }

  @Override
  public IElementType getTokenType() {
    return getElementType();
  }

  @Override
  public boolean isTextBlock() {
    final IElementType token = getElementType();
    return token == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN ||
           token == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID ||
           token == JavaTokenType.TEXT_BLOCK_TEMPLATE_END;
  }

  @Override
  public String toString(){
    return "PsiFragment:" + getElementType();
  }
}