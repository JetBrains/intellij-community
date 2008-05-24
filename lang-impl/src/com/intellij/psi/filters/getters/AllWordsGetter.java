package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.WordCompletionData;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.impl.cache.impl.id.IdTableBuilding;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

public class AllWordsGetter implements ContextGetter {
  public Object[] get(final PsiElement context, final CompletionContext completionContext) {
    if (StringUtil.isEmpty(WordCompletionData.findPrefixSimple(context, completionContext.getStartOffset()))) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    
    final CharSequence chars = context.getContainingFile().getViewProvider().getContents(); // ??
    final List<String> objs = new ArrayList<String>();
    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor() {
      public void run(final CharSequence chars, final int start, final int end, char[] charArray) {
        if (completionContext == null || start > completionContext.getStartOffset() || completionContext.getStartOffset() > end) {
          objs.add(chars.subSequence(start, end).toString());
        }
      }
    }, chars, 0, chars.length());
    return objs.toArray();
  }
}
