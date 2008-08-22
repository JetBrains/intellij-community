package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

public class AllWordsGetter implements ContextGetter {
  public Object[] get(final PsiElement context, final CompletionContext completionContext) {
    final int offset = completionContext.getStartOffset();
    return getAllWords(context, offset);
  }

  public static String[] getAllWords(final PsiElement context, final int offset) {
    if (StringUtil.isEmpty(CompletionUtil.findJavaIdentifierPrefix(context, offset))) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    final CharSequence chars = context.getContainingFile().getViewProvider().getContents(); // ??
    final List<String> objs = new ArrayList<String>();
    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor() {
      public void run(final CharSequence chars, final int start, final int end, char[] charArray) {
        if (start > offset || offset > end) {
          objs.add(chars.subSequence(start, end).toString());
        }
      }
    }, chars, 0, chars.length());
    return objs.toArray(new String[objs.size()]);
  }
}
