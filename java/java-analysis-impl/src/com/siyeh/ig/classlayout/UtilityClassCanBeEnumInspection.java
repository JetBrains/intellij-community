// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassInitializer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Bas Leijdekkers
 */
public final class UtilityClassCanBeEnumInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("utility.class.code.can.be.enum.problem.descriptor");
  }

  @Override
  protected @NotNull LocalQuickFix buildFix(Object... infos) {
    return new UtilityClassCanBeEnumFix();
  }

  private static class UtilityClassCanBeEnumFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @Nls @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("utility.class.code.can.be.enum.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!PsiUtil.isAvailable(JavaFeature.ENUMS, element) || !(element.getParent() instanceof PsiClass aClass)) {
        return;
      }
      final PsiKeyword keyword = PsiTreeUtil.getChildOfType(aClass, PsiKeyword.class);
      if (keyword == null) {
        return;
      }
      for (PsiMethod constructor : aClass.getConstructors()) {
        constructor.delete();
      }
      final PsiModifierList modifierList = aClass.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.FINAL, false);
        modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
        modifierList.setModifierProperty(PsiModifier.STATIC, false); // remove redundant modifier because nested enum is implicitly static
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiStatement statement = factory.createStatementFromText(";", element);
      final PsiElement token = statement.getFirstChild();
      aClass.addAfter(token, aClass.getLBrace());
      keyword.replace(factory.createKeyword(JavaKeywords.ENUM));
    }
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
    return new UtilityClassCanBeEnumVisitor();
  }

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.ENUMS);
  }

  private static class UtilityClassCanBeEnumVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      if (aClass.isEnum() 
          || !UtilityClassUtil.isUtilityClass(aClass, true, true) 
          || !UtilityClassUtil.hasPrivateEmptyOrNoConstructor(aClass)) {
        return;
      }
      for (PsiField field : aClass.getFields()) {
        if (!field.hasModifierProperty(PsiModifier.FINAL) || !PsiUtil.isCompileTimeConstant(field)) {
          // It's a compile error when a non-constant is accessed from an initializer or constructor in an enum
          for (PsiReference reference : VariableAccessUtils.getVariableReferences(field)) {
            // no need to check constructors, or instance field, because utility classes only have empty constructors and static fields
            final PsiClassInitializer initializer =
              PsiTreeUtil.getParentOfType(reference.getElement(), PsiClassInitializer.class, true, PsiClass.class);
            if (initializer != null && !initializer.hasModifierProperty(PsiModifier.STATIC)) {
              return;
            }
          }
        }
      }
      if (!ReferencesSearch.search(aClass).forEach(ref -> !(ref.getElement().getParent() instanceof PsiNewExpression))) {
        return;
      }
      registerClassError(aClass);
    }
  }
}
