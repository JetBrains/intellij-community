// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class MethodOnlyUsedFromInnerClassInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreMethodsAccessedFromAnonymousClass = false;

  @SuppressWarnings({"PublicField", "unused"})
  public boolean ignoreStaticMethodsFromNonStaticInnerClass = false; // Preserved for serialization compatibility

  @SuppressWarnings("PublicField")
  public boolean onlyReportStaticMethods = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass containingClass = (PsiClass)infos[0];
    final int ordinal = ClassUtils.getTypeOrdinal(containingClass);
    final String name = containingClass instanceof PsiAnonymousClass
           ? ((PsiAnonymousClass)containingClass).getBaseClassReference().getText()
           : containingClass.getName();
    final int innerClassType = containingClass instanceof PsiAnonymousClass ? 3 : PsiUtil.isLocalClass(containingClass) ? 2 : 1;
    return InspectionGadgetsBundle.message("method.only.used.from.inner.class.problem.descriptor", innerClassType, ordinal, name);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreMethodsAccessedFromAnonymousClass",
               InspectionGadgetsBundle.message("method.only.used.from.inner.class.ignore.option")),
      checkbox("onlyReportStaticMethods", InspectionGadgetsBundle.message("only.report.static.methods")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodOnlyUsedFromInnerClassVisitor();
  }

  private class MethodOnlyUsedFromInnerClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (!method.hasModifierProperty(PsiModifier.PRIVATE) || method.isConstructor()) {
        return;
      }
      if (onlyReportStaticMethods && !method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      if (DeclarationSearchUtils.isTooExpensiveToSearch(method, false)) {
        registerPossibleProblem(method.getNameIdentifier());
        return;
      }
      final MethodReferenceFinder processor = new MethodReferenceFinder(method);
      final PsiClass innerClass = processor.getOnlyAccessInnerClass();
      if (innerClass == null) {
        return;
      }
      if (method.hasModifierProperty(PsiModifier.STATIC) && !HighlightingFeature.INNER_STATICS.isAvailable(method)) {
        final PsiElement parent = innerClass.getParent();
        if (parent instanceof PsiClass && !innerClass.hasModifierProperty(PsiModifier.STATIC) || PsiUtil.isLocalClass(innerClass)) {
          return;
        }
      }
      if (ignoreMethodsAccessedFromAnonymousClass && PsiUtil.isLocalOrAnonymousClass(innerClass)) {
        return;
      }
      registerMethodError(method, innerClass);
    }
  }

  private static class MethodReferenceFinder implements Processor<PsiReference> {

    private final PsiClass methodClass;
    private final PsiMethod method;
    private PsiClass myContainingClass = null;

    MethodReferenceFinder(@NotNull PsiMethod method) {
      this.method = method;
      methodClass = method.getContainingClass();
    }

    @Override
    public boolean process(PsiReference reference) {
      final PsiElement element = reference.getElement();
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method.equals(containingMethod)) { // recursive call
        return true;
      }
      final PsiClass containingClass = ClassUtils.getContainingClass(element);
      if (containingClass == null) {
        return false;
      }
      if (myContainingClass != null) {
        if (!myContainingClass.equals(containingClass)) {
          return false;
        }
      }
      else if (!PsiTreeUtil.isAncestor(methodClass, containingClass, true)) {
        return false;
      }
      myContainingClass = containingClass;
      return true;
    }

    public PsiClass getOnlyAccessInnerClass() {
      return ReferencesSearch.search(method).forEach(this) ? myContainingClass : null;
    }
  }
}