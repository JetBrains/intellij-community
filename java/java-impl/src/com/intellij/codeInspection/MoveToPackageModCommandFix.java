// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveToPackageModCommandFix extends PsiBasedModCommandAction<PsiFile> {
  private final String myTargetPackage;

  public MoveToPackageModCommandFix(@NotNull PsiFile psiFile, @NotNull String targetPackage) {
    super(psiFile);
    myTargetPackage = targetPackage;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiFile file) {
    if (!isAvailable(file, myTargetPackage)) return null;
    return Presentation.of(QuickFixBundle.message("move.class.to.package.text", myTargetPackage));
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("move.class.to.package.family");
  }

  @Contract("null, _ -> null; _, null -> null")
  public static @Nullable ModCommandAction createIfAvailable(@Nullable PsiFile file, @Nullable String targetPackage) {
    if (!isAvailable(file, targetPackage)) {
      return null;
    }
    return new MoveToPackageModCommandFix(file, targetPackage);
  }

  @Contract("null, _ -> false; _, null -> false")
  private static boolean isAvailable(@Nullable PsiFile file, @Nullable String targetPackage) {
    if (!(file instanceof PsiJavaFile javaFile)
        || !file.isValid()
        || !file.getManager().isInProject(file)
        || javaFile.getClasses().length == 0
        || targetPackage == null) {
      return false;
    }
    Project project = file.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    PsiDirectory directory = file.getContainingDirectory();
    if (directory == null) return false;
    VirtualFile sourceRoot = fileIndex.getSourceRootForFile(directory.getVirtualFile());
    return sourceRoot != null && correctPackage(targetPackage, project, sourceRoot) != null;
  }

  private static @Nullable String correctPackage(@NotNull String targetPackage, @NotNull Project project, @NotNull VirtualFile sourceRoot) {
    String sourceRootPackage = PackageIndex.getInstance(project).getPackageNameByDirectory(sourceRoot);
    if (sourceRootPackage != null && !sourceRootPackage.isEmpty()) {
      if (targetPackage.equals(sourceRootPackage)) return "";
      if (!targetPackage.startsWith(sourceRootPackage + ".")) return null;
      return targetPackage.substring(sourceRootPackage.length() + 1);
    }
    return targetPackage;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiFile file) {
    Project project = context.project();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    PsiDirectory directory = file.getContainingDirectory();
    VirtualFile sourceRoot = fileIndex.getSourceRootForFile(directory.getVirtualFile());
    if (sourceRoot == null) return ModCommand.nop();
    String targetPackage = correctPackage(myTargetPackage, project, sourceRoot);
    if (targetPackage == null) return ModCommand.nop();

    ModCommand result = ModCommand.nop();
    VirtualFile targetDir = sourceRoot;
    for (String name : targetPackage.split("\\.")) {
      VirtualFile next = targetDir.findChild(name);
      if (next == null) {
        FutureVirtualFile toCreate = new FutureVirtualFile(targetDir, name, null);
        result = result.andThen(new ModCreateFile(toCreate, new ModCreateFile.Directory()));
        targetDir = toCreate;
      } else {
        if (!next.isDirectory()) {
          return ModCommand.error(JavaBundle.message("validator.text.not.directory"));
        }
        targetDir = next;
      }
    }
    VirtualFile sourceFile = file.getVirtualFile();
    return result.andThen(new ModMoveFile(sourceFile, new FutureVirtualFile(targetDir, sourceFile.getName(), sourceFile.getFileType())));
  }
}
