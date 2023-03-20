// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.todo.nodes;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.impl.nodes.PackageElement;
import com.intellij.ide.projectView.impl.nodes.PackageUtil;
import com.intellij.ide.todo.TodoTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TodoJavaTreeHelper extends TodoTreeHelper {

  public TodoJavaTreeHelper(final Project project) {
    super(project);
  }

  @Override
  public boolean skipDirectory(final PsiDirectory directory) {
    return JavaDirectoryService.getInstance().getPackage(directory) != null;
  }

  @Override
  public PsiElement getSelectedElement(final Object userObject) {
    if (userObject instanceof TodoPackageNode descriptor) {
      final PackageElement packageElement = descriptor.getValue();
      return packageElement != null ? packageElement.getPackage() : null;
    }
    return super.getSelectedElement(userObject);
  }

  @Override
  public boolean contains(ProjectViewNode node, Object element) {
    if (element instanceof PackageElement) {
      for (VirtualFile virtualFile : ((PackageElement)element).getRoots()) {
        if (node.contains(virtualFile)) return true;
      }
    }
    return super.contains(node, element);
  }

  @Override
  public void addPackagesToChildren(@NotNull final ArrayList<? super AbstractTreeNode<?>> children,
                                    @Nullable final Module module,
                                    @NotNull final TodoTreeBuilder builder) {
    Project project = getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final List<VirtualFile> sourceRoots = new ArrayList<>();
    if (module == null) {
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
      ContainerUtil.addAll(sourceRoots, projectRootManager.getContentSourceRoots());
    }
    else {
      ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      ContainerUtil.addAll(sourceRoots, moduleRootManager.getSourceRoots());
    }
    final Set<PsiPackage> topLevelPackages = new HashSet<>();
    for (final VirtualFile root : sourceRoots) {
      final PsiDirectory directory = psiManager.findDirectory(root);
      if (directory == null) {
        continue;
      }
      final PsiPackage directoryPackage = JavaDirectoryService.getInstance().getPackage(directory);
      if (directoryPackage == null || PackageUtil.isPackageDefault(directoryPackage)) {
        // add subpackages
        final PsiDirectory[] subdirectories = directory.getSubdirectories();
        for (PsiDirectory subdirectory : subdirectories) {
          final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(subdirectory);
          if (aPackage != null && !PackageUtil.isPackageDefault(aPackage)) {
            topLevelPackages.add(aPackage);
          } else {
            final Iterator<PsiFile> files = builder.getFiles(subdirectory);
            if (!files.hasNext()) continue;
            TodoDirNode dirNode = new TodoDirNode(project, subdirectory, builder);
            if (!children.contains(dirNode)){
              children.add(dirNode);
            }
          }
        }
        // add non-dir items
        final Iterator<PsiFile> filesUnderDirectory = builder.getFilesUnderDirectory(directory);
        while (filesUnderDirectory.hasNext()) {
          final PsiFile file = filesUnderDirectory.next();
          TodoFileNode todoFileNode = new TodoFileNode(project, file, builder, false);
          if (!children.contains(todoFileNode)){
            children.add(todoFileNode);
          }
        }
      }
      else {
        // this is the case when a source root has pakage prefix assigned
        topLevelPackages.add(directoryPackage);
      }
    }

    GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(project);
    ArrayList<PsiPackage> packages = new ArrayList<>();
    for (PsiPackage psiPackage : topLevelPackages) {
      final PsiPackage aPackage = findNonEmptyPackage(psiPackage, module, project, builder, scope);
      if (aPackage != null){
        packages.add(aPackage);
      }
    }
    for (PsiPackage psiPackage : packages) {
      if (!builder.getTodoTreeStructure().getIsFlattenPackages()) {
        PackageElement element = new PackageElement(module, psiPackage, false);
        TodoPackageNode packageNode = new TodoPackageNode(project, element, builder, psiPackage.getQualifiedName());
        if (!children.contains(packageNode)) {
          children.add(packageNode);
        }
      } else {
        Set<PsiPackage> allPackages = new HashSet<>();
        traverseSubPackages(psiPackage, module, builder, project, allPackages);
        for (PsiPackage aPackage : allPackages) {
          TodoPackageNode packageNode = new TodoPackageNode(project, new PackageElement(module, aPackage, false), builder);
          if (!children.contains(packageNode)) {
            children.add(packageNode);
          }
        }
      }
    }
    final List<? extends VirtualFile> roots = collectContentRoots(module);
    roots.removeAll(sourceRoots);
    addDirsToChildren(roots, children, builder);
  }

   @Nullable
  public static PsiPackage findNonEmptyPackage(@NotNull PsiPackage rootPackage, Module module, Project project, TodoTreeBuilder builder, GlobalSearchScope scope){
    if (!isPackageEmpty(new PackageElement(module, rootPackage, false), builder, project)){
      return rootPackage;
    }
    final PsiPackage[] subPackages = rootPackage.getSubPackages(scope);
    PsiPackage suggestedNonEmptyPackage = null;
    int count = 0;
    for (PsiPackage aPackage : subPackages) {
      if (!isPackageEmpty(new PackageElement(module, aPackage, false), builder, project)){
        if (++ count > 1) return rootPackage;
        suggestedNonEmptyPackage = aPackage;
      }
    }
    for (PsiPackage aPackage : subPackages) {
      if (aPackage != suggestedNonEmptyPackage) {
        PsiPackage subPackage = findNonEmptyPackage(aPackage, module, project, builder, scope);
        if (subPackage != null){
          if (count > 0){
            return rootPackage;
          } else {
            count ++;
            suggestedNonEmptyPackage = subPackage;
          }
        }
      }
    }
    return suggestedNonEmptyPackage;
  }

  private static void traverseSubPackages(PsiPackage psiPackage, Module module, TodoTreeBuilder builder, Project project, Set<? super PsiPackage> packages){
    if (!isPackageEmpty(new PackageElement(module, psiPackage,  false), builder, project)){
      packages.add(psiPackage);
    }
    GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(project);
    final PsiPackage[] subPackages = psiPackage.getSubPackages(scope);
    for (PsiPackage subPackage : subPackages) {
      traverseSubPackages(subPackage, module, builder, project, packages);
    }
  }

  private static boolean isPackageEmpty(PackageElement packageElement, TodoTreeBuilder builder, Project project) {
    if (packageElement == null) return true;
    final PsiPackage psiPackage = packageElement.getPackage();
    final Module module = packageElement.getModule();
    GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleScope(module) : GlobalSearchScope.projectScope(project);
    final PsiDirectory[] directories = psiPackage.getDirectories(scope);
    boolean isEmpty = true;
    for (PsiDirectory psiDirectory : directories) {
      isEmpty &= builder.isDirectoryEmpty(psiDirectory);
    }
    return isEmpty;
  }
}
