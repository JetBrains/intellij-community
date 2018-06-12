// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 *  @author dsl
 */
public class RawTypeCanBeGenericInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitVariable(PsiVariable variable) {
        super.visitVariable(variable);
        final PsiTypeElement variableTypeElement = variable.getTypeElement();
        if (variableTypeElement != null) {
          final PsiType type = getSuggestedType(variable);
          if (type != null) {
            final String typeText = type.getPresentableText();
            final String message =
              InspectionsBundle.message("inspection.raw.variable.type.can.be.generic.quickfix", variable.getName(), typeText);
            final PsiElement beforeInitializer =
              PsiTreeUtil.skipWhitespacesAndCommentsBackward(variable.getInitializer());
            final ProblemDescriptor descriptor =
              holder.getManager().createProblemDescriptor(variableTypeElement,
                                                          beforeInitializer != null ? beforeInitializer : variableTypeElement,
                                                          message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                          isOnTheFly, new MyLocalQuickFix(message));
            holder.registerProblem(descriptor);
          }
        }
      }
    };
  }

  @Nullable
  private static PsiType getSuggestedType(@NotNull PsiVariable variable) {
    final PsiExpression initializer = variable.getInitializer();
    if (initializer == null) return null;
    final PsiType variableType = variable.getType();
    final PsiType initializerType = initializer.getType();
    if (!(variableType instanceof PsiClassType)) return null;
    final PsiClassType variableClassType = (PsiClassType) variableType;
    if (!variableClassType.isRaw()) return null;
    if (!(initializerType instanceof PsiClassType)) return null;
    final PsiClassType initializerClassType = (PsiClassType) initializerType;
    if (initializerClassType.isRaw()) return null;
    final PsiClassType.ClassResolveResult variableResolveResult = variableClassType.resolveGenerics();
    final PsiClassType.ClassResolveResult initializerResolveResult = initializerClassType.resolveGenerics();
    if (initializerResolveResult.getElement() == null) return null;
    PsiClass variableResolved = variableResolveResult.getElement();
    if (variableResolved == null) return null;
    PsiSubstitutor targetSubstitutor = TypeConversionUtil.getClassSubstitutor(variableResolved, initializerResolveResult.getElement(), initializerResolveResult.getSubstitutor());
    if (targetSubstitutor == null) return null;
    PsiType type = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory().createType(variableResolved, targetSubstitutor);
    if (variableType.equals(type)) return null;
    return type;
  }

  private static class MyLocalQuickFix implements LocalQuickFix {
    private final String myName;

    public MyLocalQuickFix(@NotNull String name) {
      myName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.raw.variable.type.can.be.generic.family.quickfix");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getStartElement().getParent();
      if (element instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)element;
        final PsiType type = getSuggestedType(variable);
        if (type != null) {
          final TypeMigrationRules rules = new TypeMigrationRules(project);
          rules.setBoundScope(PsiSearchHelper.getInstance(project).getUseScope(variable));
          TypeMigrationProcessor.runHighlightingTypeMigration(project, null, rules, variable, type, false, true);
        }
      }
    }
  }
}
