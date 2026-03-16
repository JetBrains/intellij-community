// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.wrongPackageStatement.AdjustPackageNameFix;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.SingleFileSourcesTracker;
import com.intellij.openapi.util.Predicates;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.JavaFeature;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.ImplicitlyImportedElement;
import com.intellij.psi.ImplicitlyImportedModule;
import com.intellij.psi.ImplicitlyImportedStaticMember;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportModuleStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.psi.util.JvmMainMethodSearcher;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        ReplaceWithExplicitClassFix fix = new ReplaceWithExplicitClassFix(aClass);
        if (InspectionProjectProfileManager.isInformationLevel(getShortName(), identifier)) {
          TextRange textRange =
            TextRange.create(0, method.getParameterList().getTextRange().getEndOffset() - method.getTextRange().getStartOffset());
          holder.problem(method, message)
            .range(textRange)
            .fix(fix)
            .register();
        }
        else {
          holder.problem(identifier, message)
            .fix(fix)
            .register();
        }
      }
    };
  }

  public static @Nullable PsiUpdateModCommandAction<PsiImplicitClass> createFix(@NotNull PsiElement psiElement) {
    PsiMember member = PsiTreeUtil.getNonStrictParentOfType(psiElement, PsiMember.class);
    if (!(member instanceof PsiMethod)) return null;
    if (!(member.getContainingClass() instanceof PsiImplicitClass implicitClass)) return null;
    boolean hasMainMethod = new JvmMainMethodSearcher() {
      @Override
      public boolean instanceMainMethodsEnabled(@NotNull PsiElement psiElement) {
        return true;
      }

      @Override
      protected boolean inheritedStaticMainEnabled(@NotNull PsiElement psiElement) {
        return true;
      }
    }.hasMainMethod(implicitClass);
    if (!hasMainMethod) return null;
    if (PsiTreeUtil.hasErrorElements(implicitClass)) {
      return null;
    }
    return new ImplicitToExplicitClassBackwardMigrationInspection.ReplaceWithExplicitClassFix(implicitClass);
  }

  public static class ReplaceWithExplicitClassFix extends PsiUpdateModCommandAction<PsiImplicitClass> {

    private ReplaceWithExplicitClassFix(@NotNull PsiImplicitClass element) {
      super(element);
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.implicit.to.explicit.class.backward.migration.fix.name");
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiImplicitClass implicitClass, @NotNull ModPsiUpdater updater) {
      PsiFile originalFile = updater.getOriginalFile(implicitClass.getContainingFile());
      String text = implicitClass.getText();
      String qualifiedName = implicitClass.getQualifiedName();
      if (qualifiedName == null) {
        return;
      }
      Project project = implicitClass.getProject();
      PsiClass newClass = PsiElementFactory.getInstance(project).createClassFromText(text, implicitClass);
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
      optimizeImport(newPsiJavaFile);
    }

    private static void optimizeImport(@NotNull PsiJavaFile newPsiJavaFile) {
      JavaCodeStyleSettings original = JavaCodeStyleSettings.getInstance(newPsiJavaFile);
      JavaCodeStyleSettings clone = (JavaCodeStyleSettings)original.clone();
      clone.setDeleteUnusedModuleImports(true);
      PsiImportList newList = new ImportHelper(clone).prepareOptimizeImportsResult(newPsiJavaFile, Predicates.alwaysTrue());
      if (newList != null) {
        final PsiImportList newImportList = newPsiJavaFile.getImportList();
        if (newImportList != null) {
          newImportList.getParent().addRangeAfter(newList.getParent().getFirstChild(), newList.getParent().getLastChild(), newImportList);
          newImportList.delete();
        }
      }
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
      if (packageName.isEmpty()) return;
      if (!PsiDirectoryFactory.getInstance(javaFile.getProject()).isValidPackageName(packageName)) return;
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
