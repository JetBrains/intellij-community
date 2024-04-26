// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.wrongPackageStatement;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.JavaErrorBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.SingleFileSourcesTracker;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.PsiDirectoryFactory;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class WrongPackageStatementInspection extends AbstractBaseJavaLocalInspectionTool {
  private static void addMoveToPackageFix(@NotNull PsiFile file, String packName, @NotNull List<? super LocalQuickFix> availableFixes) {
    MoveToPackageFix moveToPackageFix = MoveToPackageFix.createIfAvailable(file, packName);
    if (moveToPackageFix != null) {
      availableFixes.add(moveToPackageFix);
    }
  }

  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    // does not work in tests since CodeInsightTestCase copies file into temporary location
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    if (!(file instanceof PsiJavaFile javaFile)) {
      return null;
    }
    if (FileTypeUtils.isInServerPageFile(file)) return null;

    if (JavaHighlightUtil.isJavaHashBangScript(javaFile)) return null;

    PsiDirectory directory = javaFile.getContainingDirectory();
    if (directory == null) return null;
    PsiPackage dirPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (dirPackage == null) return null;
    PsiPackageStatement packageStatement = javaFile.getPackageStatement();

    String packageName = dirPackage.getQualifiedName();

    SingleFileSourcesTracker singleFileSourcesTracker = SingleFileSourcesTracker.getInstance(file.getProject());
    String singleFileSourcePackageName = singleFileSourcesTracker.getPackageNameForSingleFileSource(file.getVirtualFile());
    if (singleFileSourcePackageName != null) packageName = singleFileSourcePackageName;
    if (packageStatement == null) {
      if (!Comparing.strEqual(packageName, "", true)) {
        // highlight the first class in the file only
        PsiClass[] classes = javaFile.getClasses();
        if (classes.length == 0) return null;
        PsiIdentifier nameIdentifier = classes[0].getNameIdentifier();
        if (nameIdentifier == null) return null;

        String description;

        final LocalQuickFix fix;
        if (PsiDirectoryFactory.getInstance(file.getProject()).isValidPackageName(packageName)) {
          fix = new AdjustPackageNameFix(packageName);
          description = JavaErrorBundle.message("missing.package.statement", packageName);
        }
        else {
          fix = null;
          description = JavaErrorBundle.message("missing.package.statement.package.name.invalid", packageName);
        }
        return new ProblemDescriptor[]{manager.createProblemDescriptor(nameIdentifier, description, fix,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)};
      }
    }
    else {
      if (ContainerUtil.or(javaFile.getClasses(), c -> c instanceof PsiImplicitClass)) return null;
      final PsiJavaCodeReferenceElement packageReference = packageStatement.getPackageReference();
      PsiPackage classPackage = (PsiPackage)packageReference.resolve();
      List<LocalQuickFix> availableFixes = new ArrayList<>();
      if (classPackage == null || !Comparing.equal(packageName, packageReference.getQualifiedName(), true)) {
        if (PsiDirectoryFactory.getInstance(file.getProject()).isValidPackageName(packageName)) {
          availableFixes.add(new AdjustPackageNameFix(packageName));
        }
        String packName = classPackage != null ? classPackage.getQualifiedName() : packageReference.getQualifiedName();
        addMoveToPackageFix(file, packName, availableFixes);
      }
      if (!availableFixes.isEmpty()) {
        String description = JavaErrorBundle.message("package.name.file.path.mismatch",
                                                     packageReference.getQualifiedName(),
                                                     packageName);
        LocalQuickFix[] fixes = availableFixes.toArray(LocalQuickFix.EMPTY_ARRAY);
        ProblemDescriptor descriptor =
          manager.createProblemDescriptor(packageStatement.getPackageReference(), description, isOnTheFly,
                                          fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        return new ProblemDescriptor[]{descriptor};
      }
    }
    return null;
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "WrongPackageStatement";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
