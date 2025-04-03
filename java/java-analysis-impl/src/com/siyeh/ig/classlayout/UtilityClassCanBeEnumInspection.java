// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.UtilityClassUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
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
      if (!PsiUtil.isAvailable(JavaFeature.ENUMS, element)) {
        return;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiClass aClass)) {
        return;
      }
      for (PsiMethod constructor : aClass.getConstructors()) {
        constructor.delete();
      }
      final List<PsiKeyword> keywords = PsiTreeUtil.getChildrenOfTypeAsList(aClass, PsiKeyword.class);
      if (keywords.isEmpty()) {
        return;
      }
      final PsiModifierList modifierList = aClass.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.FINAL, false);
        modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
        modifierList.setModifierProperty(PsiModifier.STATIC, false); // remove redundant modifier because nested enum is implicitly static
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiStatement statement = factory.createStatementFromText(";", element);
      final PsiElement token = statement.getChildren()[0];
      aClass.addAfter(token, aClass.getLBrace());
      final PsiKeyword newKeyword = factory.createKeyword(JavaKeywords.ENUM);
      keywords.get(0).replace(newKeyword);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
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
      if (aClass.isEnum()) {
        return;
      }
      if (!UtilityClassUtil.isUtilityClass(aClass, true, true) || !UtilityClassUtil.hasPrivateEmptyOrNoConstructor(aClass)) {
        return;
      }
      LocalSearchScope scope = null;
      for (PsiField field : aClass.getFields()) {
        if (!field.hasModifierProperty(PsiModifier.FINAL) || !PsiUtil.isCompileTimeConstant(field)) {
          if (scope == null) {
            scope = new LocalSearchScope(new PsiElement[]{aClass}, null, true);
          }
          // It's a compile error when non-constant is accessed from initializer or constructor in an enum
          for (PsiReference reference : ReferencesSearch.search(field, scope).asIterable()) {
            // no need to check constructors, or instance field, because utility classes only have empty constructors and static fields
            final PsiClassInitializer initializer =
              PsiTreeUtil.getParentOfType(reference.getElement(), PsiClassInitializer.class, true, PsiClass.class);
            if (initializer != null && !initializer.hasModifierProperty(PsiModifier.STATIC)) {
              return;
            }
          }
        }
      }
      for (PsiReference reference : ReferencesSearch.search(aClass).asIterable()) {
        if (reference.getElement().getParent() instanceof PsiNewExpression) {
          return;
        }
      }
      registerClassError(aClass);
    }
  }
}
