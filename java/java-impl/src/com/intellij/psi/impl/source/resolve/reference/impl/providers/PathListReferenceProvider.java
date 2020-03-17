// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author davdeev
 */
public class PathListReferenceProvider extends PsiReferenceProvider {

  @Override
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    return getReferencesByElement(element);
  }

  protected boolean disableNonSlashedPaths() {
    return true;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element) {

    PsiReference[] result = PsiReference.EMPTY_ARRAY;
    final TextRange range = ElementManipulators.getValueTextRange(element);
    String s = range.substring(element.getText());
    int offset = range.getStartOffset();
    if (disableNonSlashedPaths() && !s.trim().startsWith("/")) {
      return result;
    }
    int pos = -1;
    char separator = getSeparator();
    while (true) {
      int nextPos = s.indexOf(separator, pos + 1);
      if (nextPos == -1) {
        PsiReference[] refs =
          createReferences(element, s.substring(pos + 1), pos + offset + 1, false);
        result = ArrayUtil.mergeArrays(result, refs);
        break;
      }
      else {
        PsiReference[] refs =
          createReferences(element, s.substring(pos + 1, nextPos), pos + offset + 1, false);
        result = ArrayUtil.mergeArrays(result, refs);
        pos = nextPos;
      }
    }

    return result;
  }

  protected char getSeparator() {
    return ',';
  }

  @NotNull
  protected PsiReference[] createReferences(@NotNull PsiElement element, String s, int offset, final boolean soft) {
    int contentOffset = StringUtil.findFirst(s, CharFilter.NOT_WHITESPACE_FILTER);
    if (contentOffset >= 0) {
      offset += contentOffset;
    }
    return new FileReferenceSet(s.trim(), element, offset, this, true) {

      @Override
      protected boolean isSoft() {
        return soft;
      }
    }.getAllReferences();
  }
}
