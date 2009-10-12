/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
      public void run(final CharSequence chars, final int start, final int end) {
        if (start > offset || offset > end) {
          objs.add(chars.subSequence(start, end).toString());
        }
      }
    }, chars, 0, chars.length());
    return ArrayUtil.toStringArray(objs);
  }
}
