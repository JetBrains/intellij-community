/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

/**
 * @author cdr
 */
package com.intellij.openapi.module;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.FilteredQuery;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ModuleUtil {
  public static final Key<Module> KEY_MODULE = new Key<Module>("Module");
  private ModuleUtil() {}

  public static String getModuleNameInReadAction(@NotNull final Module module) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return module.getName();
      }
    });
  }

  public static boolean isModuleDisposed(PsiElement element) {
    if (!element.isValid()) return true;
    final Project project = element.getProject();
    ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final PsiFile file = element.getContainingFile();
    if (file == null) return true;
    VirtualFile vFile = file.getVirtualFile();
    final Module module = vFile == null ? null : projectFileIndex.getModuleForFile(vFile);
    // element may be in library
    return module == null ? !projectFileIndex.isInLibraryClasses(vFile) : module.isDisposed();
  }

  @Nullable
  public static Module getParentModuleOfType(ModuleType expectedModuleType, Module module) {
    if (module == null) return null;
    if (expectedModuleType.equals(module.getModuleType())) return module;
    final List<Module> parents = getParentModulesOfType(expectedModuleType, module);
    return parents.isEmpty() ? null : parents.get(0);
  }

  @NotNull
  public static List<Module> getParentModulesOfType(ModuleType expectedModuleType, Module module) {
    final List<Module> parents = ModuleManager.getInstance(module.getProject()).getModuleDependentModules(module);
    ArrayList<Module> modules = new ArrayList<Module>();
    for (Module parent : parents) {
      if (expectedModuleType.equals(parent.getModuleType())) {
        modules.add(parent);
      }
    }
    return modules;
  }

  @Nullable
  public static Module findModuleForFile(@NotNull VirtualFile file, @NotNull Project project) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    return fileIndex.getModuleForFile(file);
  }

  @Nullable
  public static Module findModuleForPsiElement(@NotNull PsiElement element) {
    if (!element.isValid()) return null;

    Project project = element.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    if (element instanceof PsiPackage) {
      final PsiDirectory[] directories = ((PsiPackage)element).getDirectories();
      for (PsiDirectory directory : directories) {
        final Module module = fileIndex.getModuleForFile(directory.getVirtualFile());
        if (module != null) {
          return module;
        }
      }
      return null;
    }

    if (element instanceof PsiDirectory) {
      final VirtualFile vFile = ((PsiDirectory)element).getVirtualFile();
      if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
        final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
        if (orderEntries.isEmpty()) {
          return null;
        }
        Set<Module> modules = new HashSet<Module>();
        for (OrderEntry orderEntry : orderEntries) {
          modules.add(orderEntry.getOwnerModule());
        }
        final Module[] candidates = modules.toArray(new Module[modules.size()]);
        Arrays.sort(candidates, ModuleManager.getInstance(project).moduleDependencyComparator());
        return candidates[0];
      }
      return fileIndex.getModuleForFile(vFile);
    }
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      if (containingFile.getUserData(KEY_MODULE) != null) {
        return containingFile.getUserData(KEY_MODULE);
      }

      VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) {
        PsiFile originalFile = containingFile.getOriginalFile();
        if (originalFile != null) {
          virtualFile = originalFile.getVirtualFile();
        }
      }
      if (virtualFile != null) {
        return fileIndex.getModuleForFile(virtualFile);
      }
    }

    return element.getUserData(KEY_MODULE);
  }

  public static void getDependencies(@NotNull Module module, Set<Module> modules) {
    if (modules.contains(module)) return;
    modules.add(module);
    Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
    for (Module dependency : dependencies) {
      getDependencies(dependency, modules);
    }
  }

  @Nullable
  public static VirtualFile findResourceFile(final String name, final Module inModule) {
    final VirtualFile[] sourceRoots = ModuleRootManager.getInstance(inModule).getSourceRoots();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(inModule.getProject()).getFileIndex();
    for (final VirtualFile sourceRoot : sourceRoots) {
      final String packagePrefix = fileIndex.getPackageNameByDirectory(sourceRoot);
      final String prefix = packagePrefix == null || packagePrefix.length() == 0 ? null : packagePrefix.replace('.', '/') + "/";
      final String relPath = prefix != null && name.startsWith(prefix) && name.length() > prefix.length() ? name.substring(prefix.length()) : name;
      final String fullPath = sourceRoot.getPath() + "/" + relPath;
      final VirtualFile fileByPath = LocalFileSystem.getInstance().findFileByPath(fullPath);
      if (fileByPath != null) {
        return fileByPath;
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile findResourceFileInDependents(final Module searchFromModule, final String fileName) {
    return findResourceFileInScope(fileName, searchFromModule.getProject(), searchFromModule.getModuleWithDependenciesScope());
  }

  @Nullable
  public static VirtualFile findResourceFileInProject(final Project project, final String resourceName) {
    return findResourceFileInScope(resourceName, project, GlobalSearchScope.projectScope(project));
  }

  @Nullable
  public static VirtualFile findResourceFileInScope(final String resourceName,
                                                    final Project project,
                                                    final GlobalSearchScope scope) {
    int index = resourceName.lastIndexOf('/');
    String packageName = index >= 0 ? resourceName.substring(0, index).replace('/', '.') : "";
    final String fileName = index >= 0 ? resourceName.substring(index+1) : resourceName;

    final VirtualFile dir = new FilteredQuery<VirtualFile>(
      ProjectRootManager.getInstance(project).getFileIndex().getDirsByPackageName(packageName, false), new Condition<VirtualFile>() {
      public boolean value(final VirtualFile file) {
        final VirtualFile child = file.findChild(fileName);
        return child != null && scope.contains(child);
      }
    }).findFirst();
    return dir != null ? dir.findChild(fileName) : null;
  }

  public static Collection<Module> collectModulesDependsOn(@NotNull final Collection<Module> modules) {
    if (modules.isEmpty()) return Collections.emptyList();
    final HashSet<Module> result = new HashSet<Module>();
    final Project project = modules.iterator().next().getProject();
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (final Module module : modules) {
      result.add(module);
      result.addAll(moduleManager.getModuleDependentModules(module));
    }
    return result;
  }
}
