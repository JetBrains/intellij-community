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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class StaticMembersWeigher extends CompletionWeigher {
  public Comparable weigh(@NotNull LookupElement element, @Nullable CompletionLocation loc) {
    if (loc == null) {
      return null;
    }
    if (loc.getCompletionType() != CompletionType.BASIC) return 0;

    final PsiElement position = loc.getCompletionParameters().getPosition();

    // cheap weigher applicability goes first
    final Object o = element.getObject();
    if (!(o instanceof PsiMember)) return 0;
    
    if (PsiTreeUtil.getParentOfType(position, PsiDocComment.class) != null) return 0;
    if (position.getParent() instanceof PsiReferenceExpression) {
      final PsiReferenceExpression refExpr = (PsiReferenceExpression)position.getParent();
      final PsiElement qualifier = refExpr.getQualifier();
      if (qualifier == null) {
        return 0;
      }
      if (!(qualifier instanceof PsiJavaCodeReferenceElement) || !(((PsiJavaCodeReferenceElement)qualifier).resolve() instanceof PsiClass)) {
        return 0;
      }
    }

    if (((PsiMember)o).hasModifierProperty(PsiModifier.STATIC)) {
      if (o instanceof PsiMethod) return 5;
      if (o instanceof PsiField) return 4;
    }

    if (o instanceof PsiClass && ((PsiClass) o).getContainingClass() != null) {
      return 3;
    }

    //instance method or field
    return 5;
  }
}
