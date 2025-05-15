// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.javadoc;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class PackageDotHtmlMayBePackageInfoInspection extends BaseInspection {

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final boolean packageInfoExists = ((Boolean)infos[1]).booleanValue();
    if (packageInfoExists) {
      return new DeletePackageDotHtmlFix();
    }
    final String aPackage = (String)infos[0];
    return new PackageDotHtmlMayBePackageInfoFix(aPackage);
  }

  @SuppressWarnings("DialogTitleCapitalization")
  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    if ((Boolean)infos[1]) {
      return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.exists.problem.descriptor");
    }
    return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PackageDotHtmlMayBePackageInfoVisitor();
  }

  private static class DeletePackageDotHtmlFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.delete.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (element instanceof XmlFile xmlFile) {
        xmlFile.delete();
      }
    }
  }

  private static class PackageDotHtmlMayBePackageInfoFix extends PsiUpdateModCommandQuickFix {

    private final String aPackage;

    PackageDotHtmlMayBePackageInfoFix(String aPackage) {
      this.aPackage = aPackage;
    }

    @Override
    public @NotNull String getFamilyName() {
      return InspectionGadgetsBundle.message("package.dot.html.may.be.package.info.convert.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof XmlFile xmlFile)) {
        return;
      }
      final PsiDirectory directory = updater.getWritable(xmlFile.getOriginalFile().getContainingDirectory());
      if (directory == null) {
        return;
      }
      final PsiFile file = directory.findFile(PsiPackage.PACKAGE_INFO_FILE);
      if (file != null) {
        return;
      }
      final String packageInfoText = getPackageInfoText(xmlFile);
      final PsiJavaFile packageInfoFile = (PsiJavaFile)directory.createFile(PsiPackage.PACKAGE_INFO_FILE);
      final String commentText = buildCommentText(packageInfoText);
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      final PsiDocComment comment = elementFactory.createDocCommentFromText(commentText);
      if (!aPackage.isEmpty()) {
        final PsiPackageStatement packageStatement = elementFactory.createPackageStatement(aPackage);
        final PsiElement addedElement = packageInfoFile.add(packageStatement);
        packageInfoFile.addBefore(comment, addedElement);
      }
      else {
        packageInfoFile.add(comment);
      }
      CodeStyleManager.getInstance(project).reformat(packageInfoFile);
      xmlFile.delete();
      updater.moveCaretTo(packageInfoFile);
    }

    private static @NotNull String buildCommentText(String packageInfoText) {
      final StringBuilder commentText = new StringBuilder("/**\n");
      final String[] lines = StringUtil.splitByLines(packageInfoText);
      boolean appended = false;
      for (String line : lines) {
        if (!appended && line.isEmpty()) {
          // skip empty lines at the beginning
          continue;
        }
        commentText.append(" * ").append(line).append('\n');
        appended = true;
      }
      commentText.append("*/");
      return commentText.toString();
    }

    static @NotNull String getPackageInfoText(XmlFile xmlFile) {
      final XmlTag rootTag = xmlFile.getRootTag();
      if (rootTag != null) {
        final PsiElement[] children = rootTag.getChildren();
        for (PsiElement child : children) {
          if (!(child instanceof HtmlTag htmlTag)) {
            continue;
          }
          final @NonNls String name = htmlTag.getName();
          if ("body".equalsIgnoreCase(name)) {
            final XmlTagValue value = htmlTag.getValue();
            return value.getText();
          }
        }
      }
      return xmlFile.getText();
    }
  }

  private static class PackageDotHtmlMayBePackageInfoVisitor extends BaseInspectionVisitor {

    @Override
    public void visitFile(@NotNull PsiFile psiFile) {
      super.visitFile(psiFile);
      if (!(psiFile instanceof XmlFile)) {
        return;
      }
      final @NonNls String fileName = psiFile.getName();
      if (!"package.html".equals(fileName)) {
        return;
      }
      final PsiDirectory directory = psiFile.getContainingDirectory();
      if (directory == null) {
        return;
      }
      final String aPackage = getPackage(directory);
      if (aPackage == null) {
        return;
      }
      final boolean exists = directory.findFile("package-info.java") != null;
      registerError(psiFile, aPackage, exists);
    }

    public static String getPackage(@NotNull PsiDirectory directory) {
      final VirtualFile virtualFile = directory.getVirtualFile();
      final Project project = directory.getProject();
      return PackageIndex.getInstance(project).getPackageNameByDirectory(virtualFile);
    }
  }
}
