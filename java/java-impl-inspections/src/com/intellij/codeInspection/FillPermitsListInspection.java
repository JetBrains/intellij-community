// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.impl.FillPermitsListFix;
import com.intellij.java.JavaBundle;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.util.ObjectUtils.tryCast;

public final class FillPermitsListInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.SEALED_CLASSES);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass psiClass) {
        PsiIdentifier identifier = psiClass.getNameIdentifier();
        if (identifier == null) return;
        PsiFile containingFile = tryCast(psiClass.getContainingFile(), PsiJavaFile.class);
        if (containingFile == null) return;
        PsiModifierList modifiers = psiClass.getModifierList();
        if (modifiers == null || !modifiers.hasExplicitModifier(PsiModifier.SEALED)) return;
        Set<PsiClass> permittedClasses = ContainerUtil.map2Set(psiClass.getPermitsListTypes(), PsiClassType::resolve);
        DirectClassInheritorsSearch.searchAllSealedInheritors(psiClass, GlobalSearchScope.fileScope(containingFile))
          .forEach(inheritor -> {
            if (PsiUtil.isLocalOrAnonymousClass(inheritor)) return false;
            // handled in highlighter
            if (inheritor.getContainingFile() != containingFile) return false;
            if (!permittedClasses.contains(inheritor)) {
              holder.problem(identifier, JavaBundle.message("inspection.fill.permits.list.display.name"))
                .fix(new FillPermitsListFix(identifier)).register();
              return false;
            }
            return true;
          });
      }
    };
  }
}
