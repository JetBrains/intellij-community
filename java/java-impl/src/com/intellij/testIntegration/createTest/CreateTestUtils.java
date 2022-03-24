// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testIntegration.createTest;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JavaProjectRootsUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.CommonMoveClassesOrPackagesUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CreateTestUtils {
  @Nullable
  public static PsiDirectory selectTargetDirectory(String packageName, Project project, Module targetModule) throws
                                                                                                             IncorrectOperationException {
    final PackageWrapper targetPackage = new PackageWrapper(PsiManager.getInstance(project), packageName);

    final VirtualFile selectedRoot = ReadAction.compute(() -> {
      final List<VirtualFile> testFolders = computeTestRoots(targetModule);
      List<VirtualFile> roots;
      if (testFolders.isEmpty()) {
        roots = new ArrayList<>();
        List<String> urls = computeSuitableTestRootUrls(targetModule);
        for (String url : urls) {
          try {
            ContainerUtil.addIfNotNull(roots, VfsUtil.createDirectories(VfsUtilCore.urlToPath(url)));
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
        if (roots.isEmpty()) {
          JavaProjectRootsUtil.collectSuitableDestinationSourceRoots(targetModule, roots);
        }
        if (roots.isEmpty()) return null;
      }
      else {
        roots = new ArrayList<>(testFolders);
      }

      if (roots.size() == 1) {
        return roots.get(0);
      }
      else {
        PsiDirectory defaultDir = chooseDefaultDirectory(project, targetModule, targetPackage.getDirectories(), roots);
        return CommonMoveClassesOrPackagesUtil.chooseSourceRoot(targetPackage, roots, defaultDir);
      }
    });

    if (selectedRoot == null) return null;

    return WriteCommandAction.writeCommandAction(project).withName(CodeInsightBundle.message("create.directory.command"))
      .compute(() -> CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(targetPackage, selectedRoot));
  }

  @Nullable
  public static PsiDirectory chooseDefaultDirectory(Project project, Module currentModule, PsiDirectory[] directories, List<VirtualFile> roots) {
    List<PsiDirectory> dirs = new ArrayList<>();
    PsiManager psiManager = PsiManager.getInstance(project);
    for (VirtualFile file : ModuleRootManager.getInstance(currentModule).getSourceRoots(JavaSourceRootType.TEST_SOURCE)) {
      final PsiDirectory dir = psiManager.findDirectory(file);
      if (dir != null) {
        dirs.add(dir);
      }
    }
    if (!dirs.isEmpty()) {
      for (PsiDirectory dir : dirs) {
        final String dirName = dir.getVirtualFile().getPath();
        if (dirName.contains("generated")) continue;
        return dir;
      }
      return dirs.get(0);
    }
    for (PsiDirectory dir : directories) {
      final VirtualFile file = dir.getVirtualFile();
      for (VirtualFile root : roots) {
        if (VfsUtilCore.isAncestor(root, file, false)) {
          final PsiDirectory rootDir = psiManager.findDirectory(root);
          if (rootDir != null) {
            return rootDir;
          }
        }
      }
    }
    return ModuleManager.getInstance(project)
      .getModuleDependentModules(currentModule)
      .stream().flatMap(module -> ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.TEST_SOURCE).stream())
      .map(root -> psiManager.findDirectory(root)).findFirst().orElse(null);
  }

  public static List<String> computeSuitableTestRootUrls(@NotNull Module module) {
    return suitableTestSourceFolders(module).map(SourceFolder::getUrl).collect(Collectors.toList());
  }

  public static List<VirtualFile> computeTestRoots(@NotNull Module mainModule) {
    if (!computeSuitableTestRootUrls(mainModule).isEmpty()) {
      //create test in the same module, if the test source folder doesn't exist yet it will be created
      return suitableTestSourceFolders(mainModule)
        .map(SourceFolder::getFile)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    }

    //suggest to choose from all dependencies modules
    final HashSet<Module> modules = new HashSet<>();
    ModuleUtilCore.collectModulesDependsOn(mainModule, modules);
    return modules.stream()
      .flatMap(CreateTestUtils::suitableTestSourceFolders)
      .map(SourceFolder::getFile)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }

  public static Stream<SourceFolder> suitableTestSourceFolders(@NotNull Module module) {
    Predicate<SourceFolder> forGeneratedSources = JavaProjectRootsUtil::isForGeneratedSources;
    return Arrays.stream(ModuleRootManager.getInstance(module).getContentEntries())
      .flatMap(entry -> entry.getSourceFolders(JavaSourceRootType.TEST_SOURCE).stream())
      .filter(forGeneratedSources.negate());
  }
}