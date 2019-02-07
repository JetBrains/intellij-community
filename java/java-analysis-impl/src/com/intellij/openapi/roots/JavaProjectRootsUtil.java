// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
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

import java.util.*;

/**
 * @author nik
 */
public class JavaProjectRootsUtil {
  private static final Logger LOG = Logger.getInstance(JavaProjectRootsUtil.class);

  public static boolean isOutsideJavaSourceRoot(@Nullable PsiFile psiFile) {
    if (psiFile == null) return false;
    if (psiFile instanceof PsiCodeFragment) return false;
    final VirtualFile file = psiFile.getVirtualFile();
    if (file == null) return false;
    if (file.getFileSystem() instanceof NonPhysicalFileSystem) return false;
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex();
    return !projectFileIndex.isUnderSourceRootOfType(file, JavaModuleSourceRootTypes.SOURCES) && !projectFileIndex.isInLibrary(file);
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

  public static void collectSuitableDestinationSourceRoots(@NotNull Module module, @NotNull List<? super VirtualFile> result) {
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

  /**
   * Returns order entries which are exported to {@code module} from its direct {@code dependency}, and which aren't available via other dependencies.
   * @return map from a direct or transitive dependency of {@code dependency} parameter to a corresponding direct dependency of {@code dependency} parameter.
   */
  @NotNull
  public static Map<OrderEntry, OrderEntry> findExportedDependenciesReachableViaThisDependencyOnly(@NotNull Module module,
                                                                                                   @NotNull Module dependency,
                                                                                                   @NotNull RootModelProvider rootModelProvider) {
    ModuleOrderEntry moduleOrderEntry = OrderEntryUtil.findModuleOrderEntry(rootModelProvider.getRootModel(module), dependency);
    if (moduleOrderEntry == null) {
      throw new IllegalArgumentException("Cannot find dependency from " + module + " to " + dependency);
    }

    Condition<OrderEntry> withoutThisDependency = entry -> !(entry instanceof ModuleOrderEntry && entry.getOwnerModule().equals(module) &&
                                                             dependency.equals(((ModuleOrderEntry)entry).getModule()));
    OrderEnumerator enumerator =
      rootModelProvider.getRootModel(module).orderEntries()
        .satisfying(withoutThisDependency)
        .using(rootModelProvider)
        .compileOnly()
        .recursively().exportedOnly();
    if (moduleOrderEntry.getScope().isForProductionCompile()) {
      enumerator = enumerator.productionOnly();
    }

    Set<Module> reachableModules = new HashSet<>();
    Set<Library> reachableLibraries = new HashSet<>();
    enumerator.forEach(entry -> {
      if (entry instanceof ModuleSourceOrderEntry) {
        reachableModules.add(entry.getOwnerModule());
      }
      else if (entry instanceof ModuleOrderEntry) {
        ContainerUtil.addIfNotNull(reachableModules, ((ModuleOrderEntry)entry).getModule());
      }
      else if (entry instanceof LibraryOrderEntry) {
        ContainerUtil.addIfNotNull(reachableLibraries, ((LibraryOrderEntry)entry).getLibrary());
      }
      return true;
    });

    Map<OrderEntry, OrderEntry> result = new LinkedHashMap<>();
    rootModelProvider.getRootModel(dependency).orderEntries().using(rootModelProvider).exportedOnly().withoutSdk().withoutModuleSourceEntries().forEach(direct -> {
      if (direct instanceof ModuleOrderEntry) {
        Module depModule = ((ModuleOrderEntry)direct).getModule();
        if (depModule != null && !reachableModules.contains(depModule)) {
          result.put(direct, direct);
          rootModelProvider.getRootModel(depModule).orderEntries().using(rootModelProvider).exportedOnly().withoutSdk().recursively().forEach(transitive -> {
            if (transitive instanceof ModuleSourceOrderEntry && !reachableModules.contains(transitive.getOwnerModule()) && !depModule.equals(transitive.getOwnerModule())
                || transitive instanceof LibraryOrderEntry && ((LibraryOrderEntry)transitive).getLibrary() != null && !reachableLibraries.contains(((LibraryOrderEntry)transitive).getLibrary())) {
              if (!result.containsKey(transitive)) {
                result.put(transitive, direct);
              }
            }
            return true;
          });
        }
      }
      else if (direct instanceof LibraryOrderEntry && ((LibraryOrderEntry)direct).getLibrary() != null && !reachableLibraries.contains(((LibraryOrderEntry)direct).getLibrary())) {
        result.put(direct, direct);
      }
      return true;
    });
    return result;
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
