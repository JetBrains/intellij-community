/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup;

import com.intellij.compiler.classFilesIndex.chainsSearch.completion.lookup.sub.SubLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public final class ChainCompletionLookupElementUtil {
  private ChainCompletionLookupElementUtil() {
  }

  public static LookupElement createLookupElement(final PsiMethod method,
                                                  final @Nullable TIntObjectHashMap<SubLookupElement> replaceElements) {
    if (method.isConstructor()) {
      //noinspection ConstantConditions
      return LookupElementBuilder.create(String.format("%s %s", PsiKeyword.NEW, method.getContainingClass().getName()));
    } else if (method.hasModifierProperty(PsiModifier.STATIC)) {
      return new ChainCompletionMethodCallLookupElement(method, replaceElements, false, true);
    } else {
      return new ChainCompletionMethodCallLookupElement(method, replaceElements);
    }
  }

  public static String fillMethodParameters(final PsiMethod method, @Nullable final TIntObjectHashMap<SubLookupElement> replaceElements) {
    final TIntObjectHashMap<SubLookupElement> notNullReplaceElements = replaceElements == null ?
                                                                       new TIntObjectHashMap<>(0) :
        replaceElements;

    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parameters.length; i++) {
      if (i != 0) {
        sb.append(", ");
      }
      final PsiParameter parameter = parameters[i];
      final SubLookupElement replaceElement = notNullReplaceElements.get(i);
      if (replaceElement != null) {
        sb.append(replaceElement.getInsertString());
      } else {
        sb.append(parameter.getName());
      }
    }
    return sb.toString();
  }
}
