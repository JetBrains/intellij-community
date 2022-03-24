// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.ide.util.DirectoryChooserUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.AutocreatingSingleSourceRootMoveDestination;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class CommonMoveClassesOrPackagesUtil {
  private static final Logger LOG = Logger.getInstance(CommonMoveClassesOrPackagesUtil.class);

  @Nullable
  public static PsiDirectory chooseDestinationPackage(Project project, String packageName, @Nullable PsiDirectory baseDir) {
    final PsiManager psiManager = PsiManager.getInstance(project);
    final PackageWrapper packageWrapper = new PackageWrapper(psiManager, packageName);
    final PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
    PsiDirectory directory;

    PsiDirectory[] directories = aPackage != null ? aPackage.getDirectories() : null;
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final VirtualFile baseDirVirtualFile = baseDir != null ? baseDir.getVirtualFile() : null;
    final VirtualFile baseSourceRoot = baseDirVirtualFile != null ? fileIndex.getSourceRootForFile(baseDirVirtualFile) : null;
    if (directories != null &&
        directories.length == 1 &&
        baseSourceRoot != null &&
        baseSourceRoot.equals(fileIndex.getSourceRootForFile(directories[0].getVirtualFile()))) {
      directory = directories[0];
    }
    else {
      final List<VirtualFile> contentSourceRoots = JavaProjectRootsUtil.getSuitableDestinationSourceRoots(project);
      if (contentSourceRoots.size() == 1 &&
          baseSourceRoot != null &&
          baseSourceRoot.equals(contentSourceRoots.get(0))) {
        directory = WriteAction.compute(() -> CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(packageWrapper, contentSourceRoots.get(0)));
      }
      else {
        final VirtualFile sourceRootForFile = chooseSourceRoot(packageWrapper, contentSourceRoots, baseDir);
        if (sourceRootForFile == null) return null;
        directory = WriteAction.compute(
          () -> new AutocreatingSingleSourceRootMoveDestination(packageWrapper, sourceRootForFile).getTargetDirectory((PsiDirectory)null));
      }
    }
    return directory;
  }

  @Nullable
  public static VirtualFile chooseSourceRoot(@NotNull PackageWrapper targetPackage,
                                             @NotNull List<? extends VirtualFile> contentSourceRoots,
                                             @Nullable PsiDirectory initialDirectory) {
    Project project = targetPackage.getManager().getProject();
    //ensure that there would be no duplicates: e.g. when one content root is subfolder of another root (configured via excluded roots)
    LinkedHashSet<PsiDirectory> targetDirectories = new LinkedHashSet<>();
    Map<PsiDirectory, String> relativePathsToCreate = new HashMap<>();
    buildDirectoryList(targetPackage, contentSourceRoots, targetDirectories, relativePathsToCreate);

    PsiDirectory selectedDir = DirectoryChooserUtil.chooseDirectory(
      targetDirectories.toArray(PsiDirectory.EMPTY_ARRAY),
      initialDirectory,
      project,
      relativePathsToCreate);

    VirtualFile vDir = selectedDir == null ? null : selectedDir.getVirtualFile();
    return vDir == null ? null : ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(vDir);
  }

  public static void buildDirectoryList(@NotNull PackageWrapper aPackage,
                                        @NotNull List<? extends VirtualFile> contentSourceRoots,
                                        @NotNull LinkedHashSet<? super PsiDirectory> targetDirectories,
                                        @NotNull Map<PsiDirectory, String> relativePathsToCreate) {

    final PsiDirectory[] directories = aPackage.getDirectories();
    sourceRoots:
    for (VirtualFile root : contentSourceRoots) {
      if (!root.isDirectory()) continue;
      for (PsiDirectory directory : directories) {
        if (VfsUtilCore.isAncestor(root, directory.getVirtualFile(), false)) {
          targetDirectories.add(directory);
          continue sourceRoots;
        }
      }
      String qNameToCreate;
      try {
        qNameToCreate = CommonJavaRefactoringUtil.qNameToCreateInSourceRoot(aPackage, root);
      }
      catch (IncorrectOperationException e) {
        continue;
      }
      PsiDirectory currentDirectory = aPackage.getManager().findDirectory(root);
      if (currentDirectory == null) continue;
      final String[] shortNames = qNameToCreate.split("\\.");
      for (int j = 0; j < shortNames.length; j++) {
        String shortName = shortNames[j];
        final PsiDirectory subdirectory = currentDirectory.findSubdirectory(shortName);
        if (subdirectory == null) {
          targetDirectories.add(currentDirectory);
          final StringBuilder postfix = new StringBuilder();
          for (int k = j; k < shortNames.length; k++) {
            String name = shortNames[k];
            postfix.append(File.separatorChar);
            postfix.append(name);
          }
          relativePathsToCreate.put(currentDirectory, postfix.toString());
          continue sourceRoots;
        }
        else {
          currentDirectory = subdirectory;
        }
      }
    }
    LOG.assertTrue(targetDirectories.size() <= contentSourceRoots.size());
    LOG.assertTrue(relativePathsToCreate.size() <= contentSourceRoots.size());
  }
}
