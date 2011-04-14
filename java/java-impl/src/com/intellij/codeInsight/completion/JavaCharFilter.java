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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:01:22 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;

public class JavaCharFilter extends CharFilter {

  private static boolean isWithinLiteral(final Lookup lookup) {
    PsiElement psiElement = lookup.getPsiElement();
    return psiElement != null && psiElement.getParent() instanceof PsiLiteralExpression;
  }

  public Result acceptChar(char c, final int prefixLength, final Lookup lookup) {
    if (!lookup.isCompletion()) return null;

    if (!(lookup.getPsiFile() instanceof PsiJavaFile)) {
      return null;
    }

    LookupElement item = lookup.getCurrentItem();
    if (item == null) return null;

    final Object o = item.getObject();
    if (c == '!') {
      if (o instanceof PsiVariable) {
        if (PsiType.BOOLEAN.isAssignableFrom(((PsiVariable)o).getType())) return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
      if (o instanceof PsiMethod) {
        final PsiType type = ((PsiMethod)o).getReturnType();
        if (type != null && PsiType.BOOLEAN.isAssignableFrom(type)) return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }

      return null;
    }
    if (c == '.' && isWithinLiteral(lookup)) return Result.ADD_TO_PREFIX;
    if (c == '[') return CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP;
    if (c == '<' && o instanceof PsiClass) return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
    if (c == '(' && o instanceof PsiClass) {
      if (PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.NEW).accepts(lookup.getPsiElement())) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
      return Result.HIDE_LOOKUP;
    }
    if (c == ',' && o instanceof PsiVariable) {
      int lookupStart = ((LookupImpl)lookup).getLookupStart();
      String name = ((PsiVariable)o).getName();
      if (lookupStart >= 0 &&
          name != null &&
          name.equals(item.getPrefixMatcher().getPrefix() + ((LookupImpl)lookup).getAdditionalPrefix())) {
        return Result.HIDE_LOOKUP;
      }
    }

    if (c == '#' && PsiTreeUtil.getParentOfType(lookup.getPsiElement(), PsiDocComment.class) != null) {
      if (o instanceof PsiClass) {
        return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
      }
    }
    return null;
  }

}
