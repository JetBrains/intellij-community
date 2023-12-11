// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.NotNull;

public final class UtilityClassWithPublicConstructorInspection
  extends BaseInspection {


  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "utility.class.with.public.constructor.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiClass psiClass = (PsiClass)infos[0];
    final boolean hasInheritors = (Boolean)infos[1];
    if (psiClass.getConstructors().length > 1) {
      return new UtilityClassWithPublicConstructorFix(true, hasInheritors);
    }
    else {
      return new UtilityClassWithPublicConstructorFix(false, hasInheritors);
    }
  }

  private static class UtilityClassWithPublicConstructorFix
    extends PsiUpdateModCommandQuickFix {

    private final boolean m_multipleConstructors;
    private final boolean m_hasInheritors;

    UtilityClassWithPublicConstructorFix(boolean multipleConstructors, boolean hasInheritors) {
      super();
      m_multipleConstructors = multipleConstructors;
      m_hasInheritors = hasInheritors;
    }

    @Override
    @NotNull
    public String getName() {
      return m_hasInheritors ?
        InspectionGadgetsBundle.message( "utility.class.with.public.constructor.make.protected.quickfix",
                                         Integer.valueOf(m_multipleConstructors ? 2 : 1)) :
        InspectionGadgetsBundle.message( "utility.class.with.public.constructor.make.private.quickfix",
                                         Integer.valueOf(m_multipleConstructors ? 2 : 1));
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("utility.class.with.public.constructor.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement classNameIdentifier, @NotNull ModPsiUpdater updater) {
      final PsiClass psiClass = (PsiClass)classNameIdentifier.getParent();
      if (psiClass == null) {
        return;
      }

      String modifier = m_hasInheritors? PsiModifier.PROTECTED : PsiModifier.PRIVATE;

      final PsiMethod[] constructors = psiClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        final PsiModifierList modifierList =
          constructor.getModifierList();
        modifierList.setModifierProperty(modifier, true);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticClassWithPublicConstructorVisitor();
  }

  private static class StaticClassWithPublicConstructorVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      // no call to super, so that it doesn't drill down to inner classes
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }
      if (!hasPublicConstructor(aClass)) {
        return;
      }

      final SearchScope scope = GlobalSearchScope.projectScope(aClass.getProject());
      final Query<PsiClass> query = ClassInheritorsSearch.search(aClass, scope, false);
      final PsiClass subclass = query.findFirst();
      Boolean hasInheritors = subclass != null;

      registerClassError(aClass, aClass, hasInheritors);
    }

    private static boolean hasPublicConstructor(PsiClass aClass) {
      final PsiMethod[] constructors = aClass.getConstructors();
      for (final PsiMethod constructor : constructors) {
        if (constructor.hasModifierProperty(PsiModifier.PUBLIC)) {
          return true;
        }
      }
      return false;
    }
  }
}