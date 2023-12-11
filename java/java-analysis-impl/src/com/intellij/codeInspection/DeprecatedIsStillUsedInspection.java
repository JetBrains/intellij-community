// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.deprecation.DeprecationInspectionBase;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public final class DeprecatedIsStillUsedInspection extends LocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new JavaElementVisitor() {
      @Override
      public void visitIdentifier(@NotNull PsiIdentifier identifier) {
        PsiElement parent = identifier.getParent();
        if (parent instanceof PsiMember && parent instanceof PsiNameIdentifierOwner && ((PsiNameIdentifierOwner)parent).getNameIdentifier() == identifier) {
          checkMember((PsiMember)parent, identifier, holder);
        }
        else if (parent != null && parent.getParent() instanceof PsiJavaModule) {
          checkJavaModule((PsiJavaModule)parent.getParent(), holder);
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
    if (name != null) {
      ThreeState state = hasUsages(member, name, searchHelper, member.getUseScope());
      if (state == ThreeState.YES) {
        holder.registerProblem(identifier, JavaAnalysisBundle.message("deprecated.member.0.is.still.used", name));
      }
      else if (state == ThreeState.UNSURE) {
        holder.registerPossibleProblem(identifier);
      }
    }
  }

  private static void checkJavaModule(@NotNull PsiJavaModule javaModule, @NotNull ProblemsHolder holder) {
    if (!PsiImplUtil.isDeprecated(javaModule)) {
      return;
    }
    PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(javaModule.getProject());
    ThreeState state = hasUsages(javaModule, javaModule.getName(), searchHelper, javaModule.getUseScope());
    if (state == ThreeState.YES) {
      holder.registerProblem(javaModule.getNameIdentifier(),
                             JavaAnalysisBundle.message("deprecated.member.0.is.still.used", javaModule.getName()));
    }
    else if (state == ThreeState.UNSURE) {
      holder.registerPossibleProblem(javaModule.getNameIdentifier());
    }
  }

  private static boolean isDeprecated(PsiDocCommentOwner element) {
    return element.isDeprecated();
  }


  private static ThreeState hasUsages(@NotNull PsiElement element,
                                      @NotNull String name,
                                      @NotNull PsiSearchHelper psiSearchHelper,
                                      @NotNull SearchScope searchScope) {
    PsiSearchHelper.SearchCostResult cheapEnough 
      = searchScope instanceof GlobalSearchScope ?
        psiSearchHelper.isCheapEnoughToSearch(name, (GlobalSearchScope)searchScope, null, null) : null;
    if (cheapEnough == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) {
      return ThreeState.NO;
    }

    if (cheapEnough == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
      return ThreeState.UNSURE;
    }

    return ThreeState.fromBoolean(ReferencesSearch.search(element, searchScope, false)
      .anyMatch(reference -> {
        PsiElement referenceElement = reference.getElement();
        return PsiTreeUtil.getParentOfType(referenceElement, PsiImportStatementBase.class) == null &&
               !DeprecationInspectionBase.isElementInsideDeprecated(referenceElement) &&
               !PsiUtil.isInsideJavadocComment(referenceElement);
      }));
  }
}
