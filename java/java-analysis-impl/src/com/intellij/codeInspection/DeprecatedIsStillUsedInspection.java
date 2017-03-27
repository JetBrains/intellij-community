/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class DeprecatedIsStillUsedInspection extends LocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitIdentifier(PsiIdentifier identifier) {
        PsiElement parent = identifier.getParent();
        if (parent instanceof PsiMember && parent instanceof PsiNameIdentifierOwner && ((PsiNameIdentifierOwner)parent).getNameIdentifier() == identifier) {
          checkMember((PsiMember)parent, identifier, holder);
        }
        super.visitIdentifier(identifier);
      }
    };
  }

  private static void checkMember(@NotNull PsiMember member, @NotNull PsiIdentifier identifier, @NotNull ProblemsHolder holder) {
    if (!(member instanceof PsiDocCommentOwner) || !isDeprecated((PsiDocCommentOwner)member)) {
      return;
    }

    PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(member.getProject());
    String name = member.getName();
    if (name != null && hasUsages(member, name, searchHelper, member.getResolveScope())) {
      holder.registerProblem(identifier, "Deprecated member '" + name + "' is still used");
    }
  }

  private static boolean isDeprecated(PsiDocCommentOwner element) {
    return element.isDeprecated();
  }


  private static boolean hasUsages(@NotNull PsiElement element,
                                       @NotNull String name,
                                       @NotNull PsiSearchHelper psiSearchHelper,
                                       @NotNull GlobalSearchScope searchScope) {
    PsiSearchHelper.SearchCostResult cheapEnough = psiSearchHelper.isCheapEnoughToSearch(name, searchScope, null, null);
    if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES ||
        cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
      return false;
    }

    return !ReferencesSearch.search(element, searchScope, false).forEach(reference -> {
      PsiElement referenceElement = reference.getElement();
      return isInsideDeprecated(referenceElement);
    });
  }

  private static boolean isInsideDeprecated(PsiElement element) {
    PsiElement parent = element;
    while ((parent = PsiTreeUtil.getParentOfType(parent, PsiDocCommentOwner.class, true)) != null) {
      if (((PsiDocCommentOwner)parent).isDeprecated()) return true;
    }
    return false;
  }
}
