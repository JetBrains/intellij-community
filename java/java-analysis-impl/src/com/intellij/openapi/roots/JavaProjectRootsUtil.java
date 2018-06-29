// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.jps.model.java.JavaResourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JavaProjectRootsUtil {
  public static boolean isOutsideJavaSourceRoot(@Nullable PsiFile psiFile) {
    if (psiFile == null) return false;
    if (psiFile instanceof PsiCodeFragment) return false;
    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return false;
    if (file.getFileSystem() instanceof NonPhysicalFileSystem) return false;
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();
    return !projectFileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) && !projectFileIndex.isInLibrarySource(file)
           && !projectFileIndex.isInLibraryClasses(file);
  }

  /**
   * @return list of all java source roots in the project which can be suggested as a target directory for a class created by user
   */
  @NotNull
  public static List<VirtualFile> getSuitableDestinationSourceRoots(@NotNull Project project) {
    List<VirtualFile> roots = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      collectSuitableDestinationSourceRoots(module, roots);
    }
    return roots;
  }

  public static void collectSuitableDestinationSourceRoots(@NotNull Module module, @NotNull List<VirtualFile> result) {
    for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
      for (SourceFolder sourceFolder : entry.getSourceFolders(JavaModuleSourceRootTypes.SOURCES)) {
        if (!isForGeneratedSources(sourceFolder)) {
          ContainerUtil.addIfNotNull(result, sourceFolder.getFile());
        }
      }
    }
  }

  public static boolean isForGeneratedSources(SourceFolder sourceFolder) {
    JavaSourceRootProperties properties = sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.SOURCES);
    JavaResourceRootProperties resourceProperties = sourceFolder.getJpsElement().getProperties(JavaModuleSourceRootTypes.RESOURCES);
    return properties != null && properties.isForGeneratedSources() || resourceProperties != null && resourceProperties.isForGeneratedSources();
  }

  public static boolean isInGeneratedCode(@NotNull VirtualFile file, @NotNull Project project) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    Module module = fileIndex.getModuleForFile(file);
    if (module == null || module.isDisposed()) {
      return false;
    }

    VirtualFile sourceRoot = fileIndex.getSourceRootForFile(file);
    if (sourceRoot == null) return false;

    SourceFolder folder = ProjectRootsUtil.findSourceFolder(module, sourceRoot);
    return folder != null && isForGeneratedSources(folder);
  }

  public static GlobalSearchScope getScopeWithoutGeneratedSources(@NotNull GlobalSearchScope baseScope, @NotNull Project project) {
    return new NonGeneratedSourceScope(baseScope, project);
  }

  private static class NonGeneratedSourceScope extends DelegatingGlobalSearchScope {
    @NotNull private final Project myProject;

    private NonGeneratedSourceScope(@NotNull GlobalSearchScope baseScope, @NotNull Project project) {
      super(baseScope);
      myProject = project;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return super.contains(file) && !isInGeneratedCode(file, myProject);
    }
  }
}
