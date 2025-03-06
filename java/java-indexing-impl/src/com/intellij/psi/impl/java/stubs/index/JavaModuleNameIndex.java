// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class JavaModuleNameIndex extends StringStubIndexExtension<PsiJavaModule> {
  private static final int MIN_JAVA_VERSION = JavaFeature.MODULES.getMinimumLevel().feature();
  private static final JavaModuleNameIndex ourInstance = new JavaModuleNameIndex();

  public static JavaModuleNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + 2;
  }

  @Override
  public @NotNull StubIndexKey<String, PsiJavaModule> getKey() {
    return JavaStubIndexKeys.MODULE_NAMES;
  }

  /**
   * @deprecated Deprecated base method, please use {@link #getModules(String, Project, GlobalSearchScope)}
   */
  @Deprecated
  @Override
  public Collection<PsiJavaModule> get(@NotNull String name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return getModules(name, project, scope);
  }

  public Collection<PsiJavaModule> getModules(@NotNull String name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    Collection<PsiJavaModule> modules = StubIndex.getElements(getKey(), name, project, new JavaSourceFilterScope(scope), PsiJavaModule.class);
    if (modules.size() > 1) {
      modules = filterHighestVersions(project, modules);
    }
    return modules;
  }

  /**
   * Filters the given collection of Java modules to exclude redundant versions of modules,
   * preserving only the highest versions available in the project scope.
   *
   * @param project the project in which the module filtering is performed
   * @param modules a collection of Java modules to be filtered
   * @return a collection of Java modules with only the highest versions retained
   */
  @NotNull
  private static Collection<PsiJavaModule> filterHighestVersions(@NotNull Project project, @NotNull Collection<PsiJavaModule> modules) {
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);

    Set<VirtualFile> roots = new HashSet<>();
    for (PsiJavaModule javaModule : modules) {
      VirtualFile file = index.getClassRootForFile(javaModule.getContainingFile().getVirtualFile());
      ContainerUtil.addIfNotNull(roots, file);
    }

    Set<VirtualFile> filter = new HashSet<>();
    for (VirtualFile root : roots) {
      Collection<VirtualFile> descriptors = getSortedFileDescriptors(root);
      boolean found = false;
      // find the highest correct module.
      for (VirtualFile descriptor : descriptors) {
        if (!found && isCorrectModulePath(root, descriptor)) {
          found = true;
        } else {
          filter.add(descriptor);
        }
      }
    }

    // remove the same modules but with a smaller version.
    if (!filter.isEmpty()) {
      modules = ContainerUtil.filter(modules, m -> !filter.contains(m.getContainingFile().getVirtualFile()));
    }

    return modules;
  }

  /**
   * Checks if the descriptor is in the root directory or a valid versioned subdirectory.
   *
   * @param root the root directory.
   * @param descriptor the module descriptor to check.
   * @return true if the descriptor is correctly located, false otherwise.
   */
  private static boolean isCorrectModulePath(@NotNull VirtualFile root, @Nullable VirtualFile descriptor) {
    if (descriptor == null) return false;
    return root.equals(descriptor.getParent()) || version(descriptor.getParent()) >= MIN_JAVA_VERSION;
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return true;
  }

  /**
   * Collects module descriptor files (e.g., `module-info.class`) from the root and "META-INF/versions",
   * sorted by Java version (highest to lowest).
   *
   * @param root the root virtual file
   * @return a sorted collection of module descriptor files
   */
  @NotNull
  private static Collection<VirtualFile> getSortedFileDescriptors(@NotNull VirtualFile root) {
    NavigableMap<Integer, VirtualFile> results = new TreeMap<>((i1,i2) -> Integer.compare(i2, i1));
    VirtualFile rootModuleInfo = root.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE);
    if (rootModuleInfo != null) {
      results.put(MIN_JAVA_VERSION, rootModuleInfo);
    }

    VirtualFile versionsDir = root.findFileByRelativePath("META-INF/versions");
    if (versionsDir != null) {
      VirtualFile[] versions = versionsDir.getChildren();
      for (VirtualFile version : versions) {
        VirtualFile moduleInfo = version.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE);
        if (moduleInfo != null) {
          results.put(version(version), moduleInfo);
        }
      }
    }

    return results.values();
  }

  private static int version(VirtualFile dir) {
    try {
      return Integer.parseInt(dir.getName());
    }
    catch (RuntimeException ignore) {
      return Integer.MIN_VALUE;
    }
  }
}
