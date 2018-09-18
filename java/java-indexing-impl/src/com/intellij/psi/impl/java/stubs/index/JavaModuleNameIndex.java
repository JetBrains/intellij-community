// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JavaModuleNameIndex extends StringStubIndexExtension<PsiJavaModule> {
  private static final JavaModuleNameIndex ourInstance = new JavaModuleNameIndex();

  public static JavaModuleNameIndex getInstance() {
    return ourInstance;
  }

  @Override
  public int getVersion() {
    return super.getVersion() + (FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping ? 2 : 0);
  }

  @NotNull
  @Override
  public StubIndexKey<String, PsiJavaModule> getKey() {
    return JavaStubIndexKeys.MODULE_NAMES;
  }

  @Override
  public Collection<PsiJavaModule> get(@NotNull String name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    Collection<PsiJavaModule> modules = StubIndex.getElements(getKey(), name, project, new JavaSourceFilterScope(scope, true), PsiJavaModule.class);
    if (modules.size() > 1) {
      modules = filterVersions(project, modules);
    }
    return modules;
  }

  private static Collection<PsiJavaModule> filterVersions(Project project, Collection<PsiJavaModule> modules) {
    Set<VirtualFile> filter = ContainerUtil.newHashSet();

    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    for (PsiJavaModule module : modules) {
      VirtualFile root = index.getClassRootForFile(module.getContainingFile().getVirtualFile());
      if (root != null) {
        List<VirtualFile> files = descriptorFiles(root, false, false);
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
      modules = modules.stream().filter(m -> !filter.contains(m.getContainingFile().getVirtualFile())).collect(Collectors.toList());
    }

    return modules;
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping;
  }

  public static @Nullable VirtualFile descriptorFile(@NotNull VirtualFile root) {
    VirtualFile result = root.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE);
    if (result == null) {
      result = ContainerUtil.getFirstItem(descriptorFiles(root, true, true));
    }
    return result;
  }

  private static List<VirtualFile> descriptorFiles(VirtualFile root, boolean checkAttribute, boolean filter) {
    List<VirtualFile> results = ContainerUtil.newSmartList();

    ContainerUtil.addIfNotNull(results, root.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE));

    VirtualFile versionsDir = root.findFileByRelativePath("META-INF/versions");
    if (versionsDir != null && (!checkAttribute || isMultiReleaseJar(root))) {
      VirtualFile[] versions = versionsDir.getChildren();
      if (filter) {
        versions = Stream.of(versions).filter(d -> version(d) >= 9).toArray(VirtualFile[]::new);
      }
      Arrays.sort(versions, JavaModuleNameIndex::compareVersions);
      for (VirtualFile version : versions) {
        ContainerUtil.addIfNotNull(results, version.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE));
      }
    }

    return results;
  }

  private static boolean isMultiReleaseJar(VirtualFile root) {
    VirtualFile manifest = root.findFileByRelativePath(JarFile.MANIFEST_NAME);
    if (manifest != null) {
      try (InputStream stream = manifest.getInputStream()) {
        return Boolean.valueOf(new Manifest(stream).getMainAttributes().getValue(new Attributes.Name("Multi-Release")));
      }
      catch (IOException ignored) { }
    }
    return false;
  }

  private static int version(VirtualFile dir) {
    try {
      return Integer.valueOf(dir.getName());
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