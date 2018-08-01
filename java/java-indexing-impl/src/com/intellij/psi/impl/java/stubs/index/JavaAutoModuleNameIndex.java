// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static java.util.Collections.singletonMap;

public class JavaAutoModuleNameIndex extends ScalarIndexExtension<String> {
  private static final ID<String, Void> NAME = ID.create("java.auto.module.name");

  private final FileBasedIndex.InputFilter myFilter =
    file -> file.isDirectory() && file.getParent() == null && "jar".equalsIgnoreCase(file.getExtension()) && !hasDescriptor(file);

  private final DataIndexer<String, Void, FileContent> myIndexer =
    data -> singletonMap(LightJavaModule.moduleName(data.getFile()), null);

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public int getVersion() {
    return 2 + (FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping ? 2 : 0);
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return myFilter;
  }

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return myIndexer;
  }

  @NotNull
  public static Collection<VirtualFile> getFilesByKey(@NotNull String moduleName, @NotNull GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, moduleName, scope);
  }

  @NotNull
  public static Collection<String> getAllKeys(@NotNull Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }

  private static boolean hasDescriptor(VirtualFile root) {
    if (root.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE) != null) {
      return true;
    }

    if (isMultiReleaseJar(root)) {
      VirtualFile versions = root.findFileByRelativePath("META-INF/versions");
      if (versions != null) {
        for (VirtualFile version : versions.getChildren()) {
          if (version.findChild(PsiJavaModule.MODULE_INFO_CLS_FILE) != null) {
            return true;
          }
        }
      }
    }

    return false;
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
}