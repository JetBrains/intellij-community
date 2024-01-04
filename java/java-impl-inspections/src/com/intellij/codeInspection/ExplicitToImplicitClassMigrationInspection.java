// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingFeature;
import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class ExplicitToImplicitClassMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final String JAVA_SUFFIX = ".java";

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!HighlightingFeature.IMPLICIT_CLASSES.isAvailable(holder.getFile())) return PsiElementVisitor.EMPTY_VISITOR;
    return new JavaElementVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (aClass.isInterface() || aClass.isRecord() || aClass.isEnum()) {
          return;
        }

        if (aClass.getContainingClass() != null) {
          return;
        }
        PsiJavaFile file = (PsiJavaFile)aClass.getContainingFile();
        if (file.getPackageStatement() != null) {
          return;
        }

        if (file.getClasses().length != 1) {
          return;
        }

        PsiJavaModule javaModule = JavaModuleGraphUtil.findDescriptorByElement(aClass);
        if (javaModule != null) {
          return;
        }

        String fileName = file.getName();
        if (!fileName.endsWith(JAVA_SUFFIX)) {
          return;
        }

        String className = aClass.getName();
        if (className == null || !className.equals(fileName.substring(0, fileName.length() - JAVA_SUFFIX.length()))) {
          return;
        }

        if (aClass.hasTypeParameters()) {
          return;
        }

        if (!PsiMethodUtil.hasMainMethod(aClass)) {
          return;
        }

        if (aClass.getExtendsListTypes().length != 0 || aClass.getImplementsListTypes().length != 0) {
          return;
        }

        if (aClass.hasModifierProperty(PsiModifier.SEALED) || aClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
          return;
        }

        PsiMethod[] constructors = aClass.getConstructors();
        if (constructors.length > 0) {
          if (constructors.length > 1) {
            return;
          }

          PsiMethod constructor = constructors[0];
          if (constructor.hasParameters() ||
              constructor.hasModifierProperty(PsiModifier.PRIVATE) ||
              (constructor.getBody() != null && constructor.getBody().getStatements().length > 0)) {
            return;
          }
        }

        Project project = aClass.getProject();
        PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(file.getPackageName());
        if (aPackage == null) {
          return;
        }
        PsiIdentifier classIdentifier = aClass.getNameIdentifier();
        if (classIdentifier == null) {
          return;
        }

        PackageScope scope = new PackageScope(aPackage, false, false);
        if (isOnTheFly) {
          final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
          final PsiSearchHelper.SearchCostResult cost =
            searchHelper.isCheapEnoughToSearch(className, scope, null, null);
          if (cost == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
            return;
          }
        }

        PsiReference first = ReferencesSearch.search(aClass, scope).findFirst();
        if (first != null) {
          return;
        }
        PsiElement lBrace = aClass.getLBrace();
        PsiElement rBrace = aClass.getRBrace();
        if (lBrace == null || rBrace == null) {
          return;
        }

        if (PsiTreeUtil.hasErrorElements(aClass)) {
          return;
        }

        holder.registerProblem(classIdentifier, JavaBundle.message("inspection.explicit.to.implicit.class.migration.name"),
                               new ReplaceWithImplicitClassFix());
      }
    };
  }


  private static class ReplaceWithImplicitClassFix extends PsiUpdateModCommandQuickFix {

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.explicit.to.implicit.class.migration.fix.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (psiClass == null) {
        return;
      }
      StringBuilder builder = new StringBuilder();
      PsiElement lBrace = psiClass.getLBrace();
      PsiElement rBrace = psiClass.getRBrace();
      if (lBrace == null || rBrace == null) {
        return;
      }
      PsiElement psiElement = lBrace.getNextSibling();
      CommentTracker tracker = new CommentTracker();
      while (psiElement != null && psiElement != rBrace) {
        builder.append(tracker.text(psiElement));
        psiElement = psiElement.getNextSibling();
      }
      PsiImplicitClass newClass = PsiElementFactory.getInstance(project).createImplicitClassFromText(builder.toString(), psiClass);
      PsiElement replaced = tracker.replace(psiClass, newClass);
      tracker.insertCommentsBefore(replaced);
    }
  }
}
