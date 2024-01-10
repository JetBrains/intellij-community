// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class ImplicitToExplicitClassBackwardMigrationInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final String SHORT_NAME =
    InspectionProfileEntry.getShortName(ImplicitToExplicitClassBackwardMigrationInspection.class.getSimpleName());

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.IMPLICIT_CLASSES.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitImplicitClass(@NotNull PsiImplicitClass aClass) {
        if (!PsiMethodUtil.hasMainMethod(aClass)) {
          return;
        }
        if (PsiTreeUtil.hasErrorElements(aClass)) {
          return;
        }
        String message = JavaBundle.message("inspection.implicit.to.explicit.class.backward.migration.name");
        if (isInfoMode()) {
          holder.registerProblem(aClass, message, new ReplaceWithExplicitClassFix());
          return;
        }

        PsiMethod method = PsiMethodUtil.findMainMethod(aClass);
        if (method == null) {
          return;
        }
        PsiIdentifier identifier = method.getNameIdentifier();
        if (identifier == null) {
          return;
        }
        holder.registerProblem(identifier, message, new ReplaceWithExplicitClassFix());
      }

      private boolean isInfoMode() {
        return InspectionProjectProfileManager.isInformationLevel(SHORT_NAME, holder.getFile());
      }
    };
  }


  private static class ReplaceWithExplicitClassFix extends PsiUpdateModCommandQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.implicit.to.explicit.class.backward.migration.fix.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiImplicitClass implicitClass;
      if (element instanceof PsiImplicitClass elementAsClass) {
        implicitClass = elementAsClass;
      }
      else {
        implicitClass = PsiTreeUtil.getParentOfType(element, PsiImplicitClass.class);
      }
      if (implicitClass == null) {
        return;
      }
      String text = implicitClass.getText();
      String qualifiedName = implicitClass.getQualifiedName();
      if (qualifiedName == null) {
        return;
      }
      PsiClass newClass = PsiElementFactory.getInstance(element.getProject()).createClassFromText(text, implicitClass);
      newClass.setName(qualifiedName);
      //user probably mostly wants to use it somewhere
      PsiModifierList modifierList = newClass.getModifierList();
      if (modifierList != null) {
        modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
      }
      implicitClass.replace(newClass);
    }
  }
}
