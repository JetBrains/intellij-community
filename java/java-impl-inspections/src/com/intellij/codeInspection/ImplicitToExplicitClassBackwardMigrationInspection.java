// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.wrongPackageStatement.AdjustPackageNameFix;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.SingleFileSourcesTracker;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

public final class ImplicitToExplicitClassBackwardMigrationInspection extends AbstractBaseJavaLocalInspectionTool {
  public static final String SHORT_NAME =
    getShortName(ImplicitToExplicitClassBackwardMigrationInspection.class.getSimpleName());

  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.IMPLICIT_CLASSES);
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitImplicitClass(@NotNull PsiImplicitClass aClass) {
        if (!PsiMethodUtil.hasMainInClass(aClass)) {
          return;
        }
        if (PsiTreeUtil.hasErrorElements(aClass)) {
          return;
        }
        String message = JavaBundle.message("inspection.implicit.to.explicit.class.backward.migration.name");

        PsiMethod method = PsiMethodUtil.findMainMethod(aClass);
        if (method == null) {
          return;
        }
        PsiIdentifier identifier = method.getNameIdentifier();
        if (identifier == null) {
          return;
        }
        if (InspectionProjectProfileManager.isInformationLevel(getShortName(), identifier)) {
          TextRange textRange =
            TextRange.create(0, method.getParameterList().getTextRange().getEndOffset() - method.getTextRange().getStartOffset());
          holder.registerProblem(method, textRange, message, new ReplaceWithExplicitClassFix());
        }
        else {
          holder.registerProblem(identifier, message, new ReplaceWithExplicitClassFix());
        }
      }
    };
  }


  private static class ReplaceWithExplicitClassFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.implicit.to.explicit.class.backward.migration.fix.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiImplicitClass implicitClass;
      PsiFile originalFile = updater.getOriginalFile(element.getContainingFile());
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
      PsiFile containingFile = implicitClass.getContainingFile();
      if (!(containingFile instanceof PsiJavaFile psiJavaFile)) {
        return;
      }
      ImplicitlyImportedElement[] elements = psiJavaFile.getImplicitlyImportedElements();
      List<ImplicitlyImportedStaticMember> staticImports = ContainerUtil.filterIsInstance(elements, ImplicitlyImportedStaticMember.class);
      List<ImplicitlyImportedModule> moduleImports = ContainerUtil.filterIsInstance(elements, ImplicitlyImportedModule.class);
      PsiElement replaced = implicitClass.replace(newClass);
      PsiJavaFile newPsiJavaFile = PsiTreeUtil.getParentOfType(replaced, PsiJavaFile.class);
      if (newPsiJavaFile == null) {
        return;
      }
      PsiImportList importList = newPsiJavaFile.getImportList();
      if (importList == null) {
        return;
      }
      addImplicitStaticImports(project, staticImports, implicitClass, importList);
      addImplicitJavaModuleImports(project, moduleImports, importList);
      addPackageStatement(newPsiJavaFile, originalFile);
      JavaCodeStyleManager.getInstance(project).optimizeImports(newPsiJavaFile);
    }

    private static void addPackageStatement(@NotNull PsiJavaFile javaFile, PsiFile originalFile) {
      PsiDirectory directory = originalFile.getContainingDirectory();
      if (directory == null) return;
      PsiPackage dirPackage = JavaDirectoryService.getInstance().getPackage(directory);
      if (dirPackage == null) return;
      PsiPackageStatement packageStatement = javaFile.getPackageStatement();
      if (packageStatement != null) return;
      String packageName = dirPackage.getQualifiedName();
      SingleFileSourcesTracker singleFileSourcesTracker = SingleFileSourcesTracker.getInstance(originalFile.getProject());
      String singleFileSourcePackageName = singleFileSourcesTracker.getPackageNameForSingleFileSource(originalFile.getVirtualFile());
      if (singleFileSourcePackageName != null) packageName = singleFileSourcePackageName;
      if(packageName.isEmpty()) return;
      if(!PsiDirectoryFactory.getInstance(javaFile.getProject()).isValidPackageName(packageName)) return;
      AdjustPackageNameFix.applyFix(javaFile, originalFile, originalFile.getContainingDirectory());
    }

    private static void addImplicitJavaModuleImports(@NotNull Project project,
                                                     @NotNull List<ImplicitlyImportedModule> moduleImports,
                                                     @NotNull PsiImportList list) {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      for (@NotNull ImplicitlyImportedModule anImport : moduleImports) {
        PsiImportModuleStatement moduleImportStatement = factory.createImportModuleStatementFromText(anImport.getModuleName());
        PsiImportModuleStatement[] declarations = list.getImportModuleStatements();
        if (declarations.length == 0) {
          list.add(moduleImportStatement);
        }
        else {
          list.addBefore(moduleImportStatement, declarations[0]);
        }
      }
    }

    private static void addImplicitStaticImports(@NotNull Project project,
                                                 @NotNull List<ImplicitlyImportedStaticMember> staticImports,
                                                 @NotNull PsiImplicitClass implicitClass,
                                                 @NotNull PsiImportList importList) {
      JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      for (@NotNull ImplicitlyImportedStaticMember importMember : staticImports) {
        PsiClass psiClass = psiFacade.findClass(importMember.getContainingClass(), implicitClass.getResolveScope());
        if (psiClass == null) {
          continue;
        }
        PsiReferenceExpressionImpl.bindToElementViaStaticImport(psiClass, importMember.getMemberName(), importList);
      }
    }
  }
}
