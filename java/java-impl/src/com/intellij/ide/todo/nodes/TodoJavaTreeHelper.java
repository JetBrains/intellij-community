/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * User: anna
 * Date: 18-Jan-2008
 */
package com.intellij.ide.todo.nodes;

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
    if (userObject instanceof TodoPackageNode) {
      TodoPackageNode descriptor = (TodoPackageNode)userObject;
      final PackageElement packageElement = descriptor.getValue();
      return packageElement != null ? packageElement.getPackage() : null;
    }
    return super.getSelectedElement(userObject);
  }

  @Override
  public void addPackagesToChildren(final ArrayList<AbstractTreeNode> children, final Module module, final TodoTreeBuilder builder) {
    Project project = getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);
    final List<VirtualFile> sourceRoots = new ArrayList<>();
    if (module == null) {
      final ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
      ContainerUtil.addAll(sourceRoots, projectRootManager.getContentSourceRoots());
    } else {
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
        for (;filesUnderDirectory.hasNext();) {
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
    super.addPackagesToChildren(children, module, builder);
  }

   @Nullable
  public static PsiPackage findNonEmptyPackage(PsiPackage rootPackage, Module module, Project project, TodoTreeBuilder builder, GlobalSearchScope scope){
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

  private static void traverseSubPackages(PsiPackage psiPackage, Module module, TodoTreeBuilder builder, Project project, Set<PsiPackage> packages){
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
