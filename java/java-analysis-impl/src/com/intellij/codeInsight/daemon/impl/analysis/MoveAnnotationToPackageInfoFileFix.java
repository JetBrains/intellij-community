// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveAnnotationToPackageInfoFileFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  protected MoveAnnotationToPackageInfoFileFix(@NotNull PsiPackageStatement pkgStatement) {
    super(pkgStatement);
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    PsiPackageStatement packageStatement = (PsiPackageStatement)startElement;
    PsiPackage aPackage = getPackage(packageStatement);
    if (aPackage == null) return false;
    PsiFile packageInfoFile = getPackageInfoFile(aPackage);
    if (packageInfoFile == null) return true;
    if (!PsiPackage.PACKAGE_INFO_FILE.equals(packageInfoFile.getName())) return false;
    PsiPackageStatement packageStatementInPackageInfoFile = PsiTreeUtil.findChildOfType(packageInfoFile, PsiPackageStatement.class);
    if (packageStatementInPackageInfoFile == null) return false;
    PsiModifierList missingAnnotations = findMissingAnnotations(packageStatement, packageStatementInPackageInfoFile);
    return missingAnnotations != null && missingAnnotations.getAnnotations().length != 0;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiPackageStatement packageStatement = (PsiPackageStatement)startElement;
    PsiPackage aPackage = getPackage(packageStatement);
    if (aPackage == null) return;
    PsiFile packageInfoFile = getPackageInfoFile(aPackage);
    if (packageInfoFile == null) {
      PsiFile createdFile = file.getContainingDirectory().createFile(PsiPackage.PACKAGE_INFO_FILE);
      createdFile.add(packageStatement);
      createdFile.navigate(true);
    }
    else if (PsiPackage.PACKAGE_INFO_FILE.equals(packageInfoFile.getName())) {
      PsiFile modifiedPackageInfoFile = moveAnnotationsAndGetFile(packageStatement, packageInfoFile);
      if (modifiedPackageInfoFile != null) {
        modifiedPackageInfoFile.navigate(true);
      }
    }
  }

  private static @Nullable PsiPackage getPackage(@NotNull PsiPackageStatement packageStatement) {
    PsiJavaCodeReferenceElement packageReference = packageStatement.getPackageReference();
    return ObjectUtils.tryCast(packageReference.resolve(), PsiPackage.class);
  }

  private static @Nullable PsiFile moveAnnotationsAndGetFile(@NotNull PsiPackageStatement packageStatement,
                                                             @NotNull PsiFile packageInfoFile) {
    PsiPackageStatement packageStatementInPackageInfoFile = PsiTreeUtil.findChildOfType(packageInfoFile, PsiPackageStatement.class);
    if (packageStatementInPackageInfoFile == null) return null;
    PsiModifierList missingAnnotations = findMissingAnnotations(packageStatement, packageStatementInPackageInfoFile);
    if (missingAnnotations == null) return null;
    PsiModifierList annotationList = packageStatementInPackageInfoFile.getAnnotationList();
    if (annotationList != null) {
      StreamEx.of(missingAnnotations.getAnnotations()).forEach(annotationList::add);
    }
    else {
      packageStatementInPackageInfoFile.addBefore(missingAnnotations, packageStatementInPackageInfoFile.getFirstChild());
    }
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
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiPackageStatement packageStatement = (PsiPackageStatement)myStartElement.getElement();
    assert packageStatement != null;
    PsiPackage aPackage = getPackage(packageStatement);
    if (aPackage == null) return IntentionPreviewInfo.EMPTY;
    PsiFile packageInfoFile = getPackageInfoFile(aPackage);
    if (packageInfoFile == null) {
      return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, PsiPackage.PACKAGE_INFO_FILE, "", packageStatement.getText());
    }
    else if (PsiPackage.PACKAGE_INFO_FILE.equals(packageInfoFile.getName())) {
      PsiFile modifiedPackageInfoFile = moveAnnotationsAndGetFile(packageStatement, (PsiFile)packageInfoFile.copy());
      if (modifiedPackageInfoFile == null) return IntentionPreviewInfo.EMPTY;
      return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, PsiPackage.PACKAGE_INFO_FILE, packageInfoFile.getText(),
                                                 modifiedPackageInfoFile.getText());
    }
    return IntentionPreviewInfo.EMPTY;
  }

  @Override
  public @NotNull String getText() {
    return getFamilyName();
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
