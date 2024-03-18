/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.psi.formatter.java;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.PsiAwareDefaultLineWrapPositionStrategy;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaLineWrapPositionStrategy extends PsiAwareDefaultLineWrapPositionStrategy {

  private final static IElementType[] ALLOWED_TYPES = {
    JavaTokenType.C_STYLE_COMMENT,
    JavaTokenType.END_OF_LINE_COMMENT,
    JavaTokenType.STRING_LITERAL,
    JavaDocTokenType.DOC_COMMENT_DATA,
    JavaTokenType.EQ,
    JavaTokenType.COMMA,
    JavaTokenType.QUEST,
    JavaTokenType.COLON
  };

  public static final String A_LINK_START = "<a";
  public static final String A_LINK_END = "</a>";

  public static final String PRE_TAG_START = "<pre";
  public static final String PRE_TAG_END = "</pre>";

  public JavaLineWrapPositionStrategy() {
    super(true, ALLOWED_TYPES);
  }

  @Override
  protected int doCalculateWrapPosition(@NotNull Document document,
                                        @Nullable Project project,
                                        @NotNull PsiElement element,
                                        int startOffset,
                                        int endOffset,
                                        int maxPreferredOffset,
                                        boolean isSoftWrap) {
    if (element.getNode().getElementType() == JavaDocTokenType.DOC_COMMENT_DATA) {
      CharSequence docChars = document.getCharsSequence();
      if (isInsidePreTag(element, docChars, startOffset) || isInsidePreTag(element, docChars, endOffset)) {
        return NO_LINE_WRAP;
      }
      int refStart = CharArrayUtil.indexOf(docChars, A_LINK_START, startOffset, endOffset);
      if (refStart >= 0 && refStart < maxPreferredOffset) {
        int refEnd = CharArrayUtil.indexOf(docChars, A_LINK_END, refStart, endOffset);
        if (refEnd >= 0 && refEnd < maxPreferredOffset) return refEnd + A_LINK_END.length();
        return refStart;
      }
    }
    int wrapPos = super.doCalculateWrapPosition(document, project, element, startOffset, endOffset, maxPreferredOffset, isSoftWrap);
    if (element.getNode().getElementType() == JavaTokenType.STRING_LITERAL) {
      TextRange range = element.getTextRange();
      if (range.getEndOffset() - wrapPos <= 1) {
        wrapPos = range.getEndOffset();
      }
      else if (wrapPos - range.getStartOffset() <= 1) {
        wrapPos = range.getStartOffset();
      }
    }
    else if (element.getNode().getElementType() == JavaTokenType.END_OF_LINE_COMMENT) {
      CharSequence docChars = document.getCharsSequence();
      TextRange range = element.getTextRange();
      int urlOffset = getUrlOffset(docChars, startOffset, endOffset);
      if (urlOffset >= 0) {
        int urlEnd = urlOffset;
        while (urlEnd < range.getEndOffset()) {
          if (StringUtil.isWhiteSpace(docChars.charAt(urlEnd))) {
            break;
          }
          urlEnd ++;
        }
        if (urlEnd > wrapPos)
          return urlEnd < range.getEndOffset() ? urlEnd : NO_LINE_WRAP;
      }
    }
    return wrapPos;
  }

  private static int getUrlOffset(@NotNull CharSequence chars, int startOffset, int endOffset) {
    @SuppressWarnings("HttpUrlsUsage") final String[] URL_PREFIX = { "http://", "https://", "ftp://"};
    for (String urlPrefix : URL_PREFIX) {
      int urlOffset = CharArrayUtil.indexOf(chars, urlPrefix, startOffset, endOffset);
      if (urlOffset >= 0) {
        return urlOffset;
      }
    }
    return -1;
  }

  private static boolean isInsidePreTag(@NotNull PsiElement element, @NotNull CharSequence chars, int offset) {
    PsiElement parent = PsiTreeUtil.findFirstParent(
      element, e -> e.getNode().getElementType() == JavaDocElementType.DOC_COMMENT);
    if (parent != null) {
      int preStart = CharArrayUtil.lastIndexOf(chars, PRE_TAG_START, offset);
      if (preStart >= 0) {
        int preEnd = CharArrayUtil.indexOf(chars, PRE_TAG_END, preStart, chars.length());
        return preEnd > offset;
      }
    }
    return false;
  }
}