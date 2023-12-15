// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveAnnotationToPackageInfoFileFix extends PsiUpdateModCommandAction<PsiPackageStatement> {

  public MoveAnnotationToPackageInfoFileFix(@NotNull PsiPackageStatement pkgStatement) {
    super(pkgStatement);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiPackageStatement packageStatement) {
    PsiPackage aPackage = getPackage(packageStatement);
    if (aPackage == null) return null;
    PsiFile packageInfoFile = getPackageInfoFile(aPackage);
    if (packageInfoFile == null) return Presentation.of(getFamilyName());
    if (!PsiPackage.PACKAGE_INFO_FILE.equals(packageInfoFile.getName())) return null;
    PsiPackageStatement packageStatementInPackageInfoFile = PsiTreeUtil.findChildOfType(packageInfoFile, PsiPackageStatement.class);
    if (packageStatementInPackageInfoFile == null) return null;
    PsiModifierList missingAnnotations = findMissingAnnotations(packageStatement, packageStatementInPackageInfoFile);
    if (missingAnnotations == null || missingAnnotations.getAnnotations().length == 0) return null;
    return Presentation.of(getFamilyName());
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiPackageStatement packageStatement, @NotNull ModPsiUpdater updater) {
    PsiPackage aPackage = getPackage(packageStatement);
    if (aPackage == null) return;
    PsiFile packageInfoFile = getPackageInfoFile(aPackage);
    if (packageInfoFile == null) {
      packageInfoFile = updater.getWritable(context.file().getContainingDirectory()).createFile(PsiPackage.PACKAGE_INFO_FILE);
    }
    if (PsiPackage.PACKAGE_INFO_FILE.equals(packageInfoFile.getName())) {
      PsiFile modifiedPackageInfoFile = moveAnnotationsAndGetFile(packageStatement, updater.getWritable(packageInfoFile));
      if (modifiedPackageInfoFile != null) {
        updater.moveCaretTo(modifiedPackageInfoFile);
      }
    }
    deleteAnnotations(packageStatement);
  }

  private static void deleteAnnotations(@NotNull PsiPackageStatement packageStatement) {
    for (PsiAnnotation annotation : packageStatement.getAnnotationList().getAnnotations()) {
      annotation.delete();
    }
  }

  private static @Nullable PsiPackage getPackage(@NotNull PsiPackageStatement packageStatement) {
    PsiJavaCodeReferenceElement packageReference = packageStatement.getPackageReference();
    return ObjectUtils.tryCast(packageReference.resolve(), PsiPackage.class);
  }

  private static @Nullable PsiFile moveAnnotationsAndGetFile(@NotNull PsiPackageStatement packageStatement,
                                                             @NotNull PsiFile packageInfoFile) {
    PsiPackageStatement packageStatementInPackageInfoFile = PsiTreeUtil.findChildOfType(packageInfoFile, PsiPackageStatement.class);
    if (packageStatementInPackageInfoFile == null) {
      PsiPackageStatement copy = (PsiPackageStatement)packageStatement.copy();
      deleteAnnotations(copy);
      packageStatementInPackageInfoFile = (PsiPackageStatement)packageInfoFile.addBefore(copy, packageInfoFile.getFirstChild());
    }
    PsiModifierList missingAnnotations = findMissingAnnotations(packageStatement, packageStatementInPackageInfoFile);
    if (missingAnnotations == null) return null;
    PsiModifierList annotationList = packageStatementInPackageInfoFile.getAnnotationList();
    if (annotationList != null) {
      StreamEx.of(missingAnnotations.getAnnotations()).forEach(annotationList::add);
    }
    else {
      packageStatementInPackageInfoFile.addBefore(missingAnnotations, packageStatementInPackageInfoFile.getFirstChild());
    }
    CodeStyleManager.getInstance(packageInfoFile.getProject()).reformat(packageInfoFile);
    return packageInfoFile;
  }

  private static @Nullable PsiModifierList findMissingAnnotations(@NotNull PsiPackageStatement packageStatement,
                                                                  @NotNull PsiPackageStatement packageStatementInPackageInfoFile) {
    PsiModifierList annotationList = packageStatementInPackageInfoFile.getAnnotationList();
    if (annotationList == null) return packageStatement.getAnnotationList();
    PsiPackageStatement copy = (PsiPackageStatement)packageStatement.copy();
    StreamEx.of(copy.getAnnotationList().getChildren())
      .select(PsiAnnotation.class)
      .filter(annotation -> {
        String qualifiedName = annotation.getQualifiedName();
        return qualifiedName != null && annotationList.hasAnnotation(qualifiedName);
      })
      .forEach(PsiElement::delete);
    return copy.getAnnotationList();
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("move.annotations.to.package.info.file.family.name");
  }

  /**
   * Returns either a `package-info.java` or `package.html` file for the specified package.
   *
   * @param aPackage the package
   * @return a `package-info.java` or `package.html` file or {@code null}
   * if the package does not contain such files.
   */
  public static @Nullable PsiFile getPackageInfoFile(@NotNull PsiPackage aPackage) {
    final PsiDirectory[] directories = aPackage.getDirectories();
    for (PsiDirectory directory : directories) {
      final PsiFile packageInfoJava = directory.findFile(PsiPackage.PACKAGE_INFO_FILE);
      if (packageInfoJava != null) return packageInfoJava;
      final PsiFile packageHtml = directory.findFile("package.html");
      if (packageHtml != null) {
        return packageHtml;
      }
    }
    return null;
  }
}
