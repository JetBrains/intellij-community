// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

    PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(member.getProject());
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
