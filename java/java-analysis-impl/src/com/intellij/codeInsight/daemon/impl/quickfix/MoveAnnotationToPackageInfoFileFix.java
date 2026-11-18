// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MoveAnnotationToPackageInfoFileFix extends PsiUpdateModCommandAction<PsiPackageStatement> {

  public MoveAnnotationToPackageInfoFileFix(@NotNull PsiPackageStatement pkgStatement) {
    super(pkgStatement);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiPackageStatement packageStatement) {
    PsiPackage aPackage = getPackage(packageStatement);
    if (aPackage == null) return null;
    PsiFile packageInfoFile = getModifiablePackageInfoFile(aPackage);
    if (packageInfoFile == null) return Presentation.of(getFamilyName());
    if (!PsiPackage.PACKAGE_INFO_FILE.equals(packageInfoFile.getName())) return null;
    PsiPackageStatement packageStatementInPackageInfoFile = PsiTreeUtil.findChildOfType(packageInfoFile, PsiPackageStatement.class);
    if (packageStatementInPackageInfoFile == null) return null;
    PsiModifierList missingAnnotations = findMissingAnnotations(packageStatement, packageStatementInPackageInfoFile);
    if (missingAnnotations == null || !missingAnnotations.hasAnnotations()) return null;
    return Presentation.of(getFamilyName());
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiPackageStatement packageStatement, @NotNull ModPsiUpdater updater) {
    PsiPackage aPackage = getPackage(packageStatement);
    if (aPackage == null) return;
    PsiFile packageInfoFile = getModifiablePackageInfoFile(aPackage);
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
   * A `package-info.java` that the user can modify comes first, because a library can share the package.
   *
   * @param aPackage the package
   * @return a `package-info.java` or `package.html` file or {@code null}
   * if the package does not contain such files.
   */
  public static @Nullable PsiFile getPackageInfoFile(@NotNull PsiPackage aPackage) {
    List<PsiFile> packageInfoFiles = aPackage.getPackageInfoFiles(GlobalSearchScope.everythingScope(aPackage.getProject()));
    PsiFile packageInfoJava = ContainerUtil.find(packageInfoFiles, BaseIntentionAction::canModify);
    if (packageInfoJava == null) packageInfoJava = ContainerUtil.getFirstItem(packageInfoFiles);
    if (packageInfoJava != null) return packageInfoJava;
    for (PsiDirectory directory : aPackage.getDirectories()) {
      PsiFile packageHtml = directory.findFile("package.html");
      if (packageHtml != null) return packageHtml;
    }
    return null;
  }

  /**
   * Returns the {@linkplain #getPackageInfoFile file} of the package that the fix can change, or {@code null}.
   * For {@code null}, the fix creates a new `package-info.java` next to the edited file.
   */
  private static @Nullable PsiFile getModifiablePackageInfoFile(@NotNull PsiPackage aPackage) {
    PsiFile packageInfoFile = getPackageInfoFile(aPackage);
    return packageInfoFile != null && BaseIntentionAction.canModify(packageInfoFile) ? packageInfoFile : null;
  }
}
