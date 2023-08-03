// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiLiteralUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class PsiFragmentImpl extends LeafPsiElement implements PsiFragment {

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

  private static String getFragmentContent(PsiFragment fragment) {
    final IElementType tokenType = fragment.getTokenType();
    final String text = fragment.getText();
    if (tokenType == JavaTokenType.STRING_TEMPLATE_BEGIN || tokenType == JavaTokenType.STRING_TEMPLATE_MID) {
      return text.substring(1, text.length() - 2);
    }
    else if (tokenType == JavaTokenType.STRING_TEMPLATE_END) {
      if (!(text.endsWith("\""))) {
        return null;
      }
      return text.substring(1, text.length() - 1);
    }

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
      content = text.substring(start - 1, text.length() - 2);
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

    final PsiElement parent = fragment.getParent();
    if (!(parent instanceof PsiTemplate)) {
      return null;
    }
    final PsiTemplate template = (PsiTemplate)parent;
    final StringBuilder sb = new StringBuilder();
    for (PsiFragment templateFragment : template.getFragments()) {
      sb.append(templateFragment.getText());
    }
    final String[] lines = PsiLiteralUtil.getTextBlockLines(sb.toString());
    if (lines == null) {
      return null;
    }
    final int indent = PsiLiteralUtil.getTextBlockIndent(lines);
    final StringBuilder result = new StringBuilder();
    final String[] split = content.split("\n", -1);
    boolean newline = false;
    for (int i = tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN ? 1 : 0; i < split.length; i++) {
      if (newline) {
        result.append('\n');
      }
      else {
        newline = true;
      }
      final String line = split[i];
      result.append(PsiLiteralUtil.trimTrailingWhitespaces((line.length() > indent) ? line.substring(indent) : line));
    }
    return result.toString();
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