// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * See <a href="https://bugs.openjdk.org/browse/JDK-8037567">...</a>
 */
public final class StaticInitializerReferencesSubClassInspection extends AbstractBaseJavaLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitField(@NotNull PsiField field) {
        checkSubClassReferences(field);
      }

      @Override
      public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
        checkSubClassReferences(initializer);
      }

      private void checkSubClassReferences(PsiMember scope) {
        if (!scope.hasModifierProperty(PsiModifier.STATIC)) return;

        PsiClass containingClass = scope.getContainingClass();
        Pair<PsiElement, PsiClass> pair = findSubClassReference(scope, containingClass);
        if (pair != null) {
          holder.registerProblem(pair.first,
                                 InspectionGadgetsBundle
                                   .message("referencing.subclass.0.from.superclass.1.initializer.might.lead.to.class.loading.deadlock",
                                            pair.second.getName(), containingClass.getName()));
        }
      }
    };
  }

  private static @Nullable Pair<PsiElement, PsiClass> findSubClassReference(@NotNull PsiElement scope, @Nullable PsiClass baseClass) {
    if (baseClass == null || baseClass.isInterface()) return null;

    final Ref<Pair<PsiElement, PsiClass>> result = Ref.create();
    scope.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiMethod ||
            element instanceof PsiReferenceParameterList ||
            element instanceof PsiTypeElement ||
            element instanceof PsiLambdaExpression) {
          return;
        }

        PsiClass targetClass = extractClass(element);
        if (targetClass != null && targetClass.isInheritor(baseClass, true) && !hasSingleInitializationPlace(targetClass, baseClass)) {
          PsiElement problemElement = calcProblemElement(element);
          if (problemElement != null) {
            result.set(Pair.create(problemElement, targetClass));
          }
        }

        super.visitElement(element);
      }
    });
    return result.get();
  }

  private static boolean hasSingleInitializationPlace(@NotNull PsiClass targetClass, @NotNull PsiClass baseClass) {
    if (!targetClass.hasModifierProperty(PsiModifier.PRIVATE)) return false;
    if (!targetClass.isInheritor(baseClass, false) && !baseClass.hasModifierProperty(PsiModifier.PRIVATE)) return false;

    PsiFile file = targetClass.getContainingFile();
    if (file == null) return false;

    LocalSearchScope scope = new LocalSearchScope(file);
    return ReferencesSearch.search(targetClass, scope).forEach(new Processor<>() {
      int count = 0;

      @Override
      public boolean process(PsiReference reference) {
        return ++count < 2;
      }
    });
  }

  private static @Nullable PsiElement calcProblemElement(PsiElement element) {
    if (element instanceof PsiNewExpression exp) return calcProblemElement(exp.getClassOrAnonymousClassReference());
    if (element instanceof PsiMethodCallExpression exp) return calcProblemElement(exp.getMethodExpression());
    if (element instanceof PsiJavaCodeReferenceElement ref) return ref.getReferenceNameElement();
    return element;
  }

  private static @Nullable PsiClass extractClass(PsiElement element) {
    if (element instanceof PsiReferenceExpression ref) {
      if (ref.resolve() instanceof PsiClass c) return c;
    }
    else if (element instanceof PsiExpression exp) {
      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(exp.getType());
      return psiClass instanceof PsiAnonymousClass ? psiClass.getSuperClass() : psiClass;
    }
    return null;
  }
}
