// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.java.codeserver.core.JavaPsiModuleUtil;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.PackageScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ipp.imports.ReplaceOnDemandImportIntention;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ExplicitToImplicitClassMigrationInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.IMPLICIT_CLASSES);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass aClass) {
        if (aClass.isInterface() || aClass.isRecord() || aClass.isEnum()) {
          return;
        }

        if (aClass.getAnnotations().length > 0) return;

        PsiElement lBrace = aClass.getLBrace();
        PsiElement rBrace = aClass.getRBrace();
        if (lBrace == null || rBrace == null) {
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

        PsiJavaModule javaModule = JavaPsiModuleUtil.findDescriptorByElement(aClass);
        if (javaModule != null) {
          return;
        }

        String fileName = file.getName();
        if (!fileName.endsWith(JavaFileType.DOT_DEFAULT_EXTENSION)) {
          return;
        }

        String className = aClass.getName();
        if (className == null ||
            !className.equals(fileName.substring(0, fileName.length() - JavaFileType.DOT_DEFAULT_EXTENSION.length()))) {
          return;
        }

        if (aClass.hasTypeParameters()) {
          return;
        }

        if (!PsiMethodUtil.hasMainMethod(aClass)) {
          return;
        }

        PsiClassType[] extendsListTypes = aClass.getExtendsListTypes();
        if ((extendsListTypes.length != 0 && !onlyObjectExtends(extendsListTypes)) ||
            aClass.getImplementsListTypes().length != 0) {
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

        Project project = holder.getProject();
        PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage("");
        if (aPackage == null) {
          return;
        }
        PsiIdentifier classIdentifier = aClass.getNameIdentifier();
        if (classIdentifier == null) {
          return;
        }

        if (TestFrameworks.getInstance().isTestClass(aClass) ||
            UnusedSymbolUtil.isImplicitUsage(aClass.getProject(), aClass)) {
          return;
        }

        PackageScope scope = new PackageScope(aPackage, false, false);
        if (isOnTheFly) {
          final PsiSearchHelper searchHelper = PsiSearchHelper.getInstance(project);
          final PsiSearchHelper.SearchCostResult cost =
            searchHelper.isCheapEnoughToSearch(className, scope, null);
          if (cost == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES) {
            return;
          }
        }

        PsiReference first = ReferencesSearch.search(aClass, scope).findFirst();
        if (first != null) {
          return;
        }

        if (PsiTreeUtil.hasErrorElements(aClass)) {
          return;
        }

        holder.registerProblem(aClass, new TextRange(0, classIdentifier.getTextRangeInParent().getEndOffset()),
                               JavaBundle.message("inspection.explicit.to.implicit.class.migration.name"),
                               new ReplaceWithImplicitClassFix());
      }

      private static boolean onlyObjectExtends(PsiClassType[] types) {
        if (types.length != 1) return false;
        PsiClassType type = types[0];
        return type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT);
      }
    };
  }


  private static class ReplaceWithImplicitClassFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.explicit.to.implicit.class.migration.fix.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiFile containingFile = element.getContainingFile();
      if (!(containingFile instanceof PsiJavaFile javaFile)) {
        return;
      }
      PsiImportList list = javaFile.getImportList();
      if (list != null) {
        List<SmartPsiElementPointer<PsiImportStatementBase>> pointers = new ArrayList<>();
        PsiImportStatementBase[] statements = list.getAllImportStatements();
        for (PsiImportStatementBase statement : statements) {
          SmartPsiElementPointer<PsiImportStatementBase> pointer = SmartPointerManager.createPointer(statement);
          pointers.add(pointer);
        }

        for (SmartPsiElementPointer<PsiImportStatementBase> pointer : pointers) {
          PsiImportStatementBase importStatementBase = pointer.getElement();
          if (importStatementBase == null) continue;
          if (!importStatementBase.isOnDemand()) continue;
          ReplaceOnDemandImportIntention.replaceOnDemand(importStatementBase);
        }
      }
      PsiClass psiClass = ObjectUtils.tryCast(element, PsiClass.class);
      if (psiClass == null) {
        return;
      }
      PsiElement lBrace = psiClass.getLBrace();
      PsiElement rBrace = psiClass.getRBrace();
      if (lBrace == null || rBrace == null || lBrace.getNextSibling() == null || rBrace.getPrevSibling() == null) {
        return;
      }

      CommentTracker tracker = new CommentTracker();
      String body = tracker.rangeText(lBrace.getNextSibling(), rBrace.getPrevSibling());
      PsiImplicitClass newClass = PsiElementFactory.getInstance(project).createImplicitClassFromText(body, psiClass);
      PsiElement replaced = tracker.replace(psiClass, newClass);
      tracker.insertCommentsBefore(replaced);

      PsiFile replacedContainingFile = replaced.getContainingFile();
      if (replacedContainingFile != null) {
        JavaCodeStyleManager.getInstance(project).optimizeImports(replacedContainingFile);
      }
    }
  }
}
