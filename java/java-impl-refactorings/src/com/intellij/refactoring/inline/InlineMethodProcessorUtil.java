// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.ElementDescriptionUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.NonCodeUsageInfoFactory;
import com.intellij.refactoring.util.NonCodeSearchDescriptionLocation;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

@ApiStatus.Internal
public final class InlineMethodProcessorUtil {
  private InlineMethodProcessorUtil() { }

  public static UsageInfo @NotNull [] findUsages(@NotNull PsiMethod method,
                                                  @Nullable PsiReference reference,
                                                  @NotNull SearchScope refactoringScope,
                                                  boolean inlineThisOnly,
                                                  boolean deleteTheDeclaration,
                                                  boolean searchInComments,
                                                  boolean searchForTextOccurrences) {
    if (inlineThisOnly) return new UsageInfo[]{new UsageInfo(Objects.requireNonNull(reference))};
    Set<UsageInfo> usages = ConcurrentCollectionFactory.createConcurrentSet();
    if (reference != null) {
      usages.add(new UsageInfo(reference.getElement()));
    }
    for (PsiReference ref : MethodReferencesSearch.search(method, refactoringScope, true).findAll()) {
      usages.add(new UsageInfo(ref.getElement()));
    }

    if (deleteTheDeclaration) {
      OverridingMethodsSearch.search(method, refactoringScope, true)
        .forEach(overridingMethod -> {
          if (shouldDeleteOverrideAttribute(method, overridingMethod)) {
            usages.add(new OverrideAttributeUsageInfo(overridingMethod));
          }
          return true;
        });
    }

    if (searchInComments || searchForTextOccurrences) {
      final NonCodeUsageInfoFactory infoFactory = new NonCodeUsageInfoFactory(method, method.getName()) {
        @Override
        public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
          if (PsiTreeUtil.isAncestor(method, usage, false)) return null;
          return super.createUsageInfo(usage, startOffset, endOffset);
        }
      };
      if (searchInComments) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(method, NonCodeSearchDescriptionLocation.STRINGS_AND_COMMENTS);
        TextOccurrencesUtil.addUsagesInStringsAndComments(method, refactoringScope, stringToSearch, usages, infoFactory);
      }

      if (searchForTextOccurrences && refactoringScope instanceof GlobalSearchScope scope) {
        String stringToSearch = ElementDescriptionUtil.getElementDescription(method, NonCodeSearchDescriptionLocation.NON_JAVA);
        TextOccurrencesUtil.addTextOccurrences(method, stringToSearch, scope, usages, infoFactory);
      }
    }

    return usages.toArray(UsageInfo.EMPTY_ARRAY);
  }

  private static boolean shouldDeleteOverrideAttribute(@NotNull PsiMethod inlinedMethod, @NotNull PsiMethod overridingMethod) {
    return ContainerUtil.and(overridingMethod.getHierarchicalMethodSignature().getSuperSignatures(), signature -> {
      PsiMethod superMethod = signature.getMethod();
      if (superMethod == inlinedMethod) {
        return true;
      }
      if (JavaLanguage.INSTANCE == overridingMethod.getLanguage() &&
          Objects.requireNonNull(superMethod.getContainingClass()).isInterface()) {
        return !PsiUtil.isAvailable(JavaFeature.OVERRIDE_INTERFACE, overridingMethod);
      }
      return false;
    });
  }

  static class OverrideAttributeUsageInfo extends UsageInfo {
    OverrideAttributeUsageInfo(@NotNull PsiElement element) {
      super(element);
    }
  }
}