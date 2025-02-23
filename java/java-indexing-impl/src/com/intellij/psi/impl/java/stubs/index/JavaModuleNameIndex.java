// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.search.JavaSourceFilterScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public final class JavaModuleNameIndex extends StringStubIndexExtension<PsiJavaModule> {
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
      modules = filterVersions(project, modules);
    }
    return modules;
  }

  private static Collection<PsiJavaModule> filterVersions(Project project, Collection<PsiJavaModule> modules) {
    Set<VirtualFile> filter = new HashSet<>();

    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    for (PsiJavaModule module : modules) {
      VirtualFile root = index.getClassRootForFile(module.getContainingFile().getVirtualFile());
      if (root != null) {
        List<VirtualFile> files = descriptorFiles(root);
        VirtualFile main = ContainerUtil.getFirstItem(files);
        if (main != null && !(root.equals(main.getParent()) || version(main.getParent()) >= 9)) {
          filter.add(main);
        }
        for (int i = 1; i < files.size(); i++) {
          filter.add(files.get(i));
        }
      }
    }

    if (!filter.isEmpty()) {
      modules = ContainerUtil.filter(modules, m -> !filter.contains(m.getContainingFile().getVirtualFile()));
    }

    return modules;
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return true;
  }

  private static List<VirtualFile> descriptorFiles(VirtualFile root) {
    List<VirtualFile> results = new SmartList<>();

    ContainerUtil.addIfNotNull(results, root.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE));

    VirtualFile versionsDir = root.findFileByRelativePath("META-INF/versions");
    if (versionsDir != null) {
      VirtualFile[] versions = versionsDir.getChildren();
      Arrays.sort(versions, JavaModuleNameIndex::compareVersions);
      for (VirtualFile version : versions) {
        ContainerUtil.addIfNotNull(results, version.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE));
      }
    }

    return results;
  }

  private static int version(VirtualFile dir) {
    try {
      return Integer.parseInt(dir.getName());
    }
    catch (RuntimeException ignore) {
      return Integer.MIN_VALUE;
    }
  }

  private static int compareVersions(VirtualFile dir1, VirtualFile dir2) {
    int v1 = version(dir1), v2 = version(dir2);
    if (v1 < 9 && v2 < 9) return 0;
    if (v1 < 9) return 1;
    if (v2 < 9) return -1;
    return v1 - v2;
  }
}
