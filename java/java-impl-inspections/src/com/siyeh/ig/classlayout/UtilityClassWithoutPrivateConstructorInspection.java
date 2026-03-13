/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.SpecialAnnotationsUtilBase;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import com.siyeh.ig.ui.ExternalizableStringSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

public final class UtilityClassWithoutPrivateConstructorInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public final ExternalizableStringSet ignorableAnnotations = new ExternalizableStringSet();
  @SuppressWarnings("PublicField")
  public boolean ignoreClassesWithOnlyMain = false;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("ignorableAnnotations", InspectionGadgetsBundle.message("ignore.if.annotated.by"),
                 new JavaClassValidator().annotationsOnly()),
      checkbox("ignoreClassesWithOnlyMain", InspectionGadgetsBundle.message("utility.class.without.private.constructor.option"))
    );
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final List<LocalQuickFix> fixes = new ArrayList<>();
    final PsiClass aClass = (PsiClass)infos[0];
    final PsiMethod constructor = getNoArgConstructor(aClass);
    if (constructor == null) {
      fixes.add(new CreateEmptyPrivateConstructor());
    }
    else {
      boolean hasInstantiation = ReferencesSearch.search(constructor, constructor.getUseScope()).findFirst() == null;
      if (hasInstantiation) {
        fixes.add(new MakeConstructorPrivateFix());
      }
    }
    fixes.addAll(SpecialAnnotationsUtilBase.createAddAnnotationToListFixes(aClass, this, insp -> insp.ignorableAnnotations));
    return fixes.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("utility.class.without.private.constructor.problem.descriptor");
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new UtilityClassWithoutPrivateConstructorVisitor();
  }

  private static boolean hasImplicitConstructorUsage(PsiClass aClass) {
    final Query<PsiReference> query = ReferencesSearch.search(aClass, aClass.getUseScope());
    return query.anyMatch(ref -> ref != null && ref.getElement().getParent() instanceof PsiNewExpression);
  }

  private static @Nullable PsiMethod getNoArgConstructor(PsiClass aClass) {
    for (PsiMethod constructor : aClass.getConstructors()) {
      if (constructor.getParameterList().isEmpty()) {
        return constructor;
      }
    }
    return null;
  }

  protected static class CreateEmptyPrivateConstructor extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("utility.class.without.private.constructor.create.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement classNameIdentifier, @NotNull ModPsiUpdater updater) {
      if (!(classNameIdentifier.getParent() instanceof PsiClass aClass)) {
        return;
      }
      if (hasImplicitConstructorUsage(aClass)) {
        updater.cancel(InspectionGadgetsBundle.message("utility.class.without.private.constructor.cant.generate.constructor.message"));
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      final PsiMethod constructor = factory.createConstructor();
      final PsiModifierList modifierList = constructor.getModifierList();
      modifierList.setModifierProperty(PsiModifier.PRIVATE, true);
      aClass.add(constructor);
      CodeStyleManager.getInstance(project).reformat(constructor);
    }
  }

  private static class MakeConstructorPrivateFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("utility.class.without.private.constructor.make.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement classNameIdentifier, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = classNameIdentifier.getParent();
      if (!(parent instanceof PsiClass aClass)) {
        return;
      }
      for (PsiMethod constructor : aClass.getConstructors()) {
        if (constructor.getParameterList().isEmpty()) {
          final PsiModifierList modifiers = constructor.getModifierList();
          modifiers.setModifierProperty(PsiModifier.PUBLIC, false);
          modifiers.setModifierProperty(PsiModifier.PROTECTED, false);
          modifiers.setModifierProperty(PsiModifier.PRIVATE, true);
        }
      }
    }
  }

  private class UtilityClassWithoutPrivateConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      if (!UtilityClassUtil.isUtilityClass(aClass)) {
        return;
      }
      if (ignoreClassesWithOnlyMain && hasOnlyMain(aClass)) {
        return;
      }
      if (hasPrivateConstructor(aClass)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(aClass, ignorableAnnotations, 0)) {
        return;
      }
      if (aClass.hasModifierProperty(PsiModifier.PRIVATE) && aClass.getConstructors().length == 0) {
        return;
      }
      boolean hasInheritor = ClassInheritorsSearch.search(aClass, aClass.getUseScope(), true).findFirst() != null;
      if (hasInheritor) {
        return;
      }
      registerClassError(aClass, aClass);
    }

    private static boolean hasOnlyMain(PsiClass aClass) {
      final PsiMethod[] methods = aClass.getMethods();
      if (methods.length == 0) {
        return false;
      }
      for (PsiMethod method : methods) {
        if (method.isConstructor() || method.hasModifierProperty(PsiModifier.PRIVATE)) {
          continue;
        }
        if (!method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.PUBLIC)) {
          return false;
        }
        if (!HardcodedMethodConstants.MAIN.equals(method.getName()) || !PsiTypes.voidType().equals(method.getReturnType())) {
          return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1) {
          return false;
        }
        final PsiType type = parameterList.getParameters()[0].getType();
        final @NonNls String stringArray = "java.lang.String[]";
        if (!type.equalsToText(stringArray)) {
          return false;
        }
      }
      return true;
    }

    boolean hasPrivateConstructor(PsiClass aClass) {
      for (PsiMethod constructor : aClass.getConstructors()) {
        if (constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
          return true;
        }
      }
      return false;
    }
  }
}
