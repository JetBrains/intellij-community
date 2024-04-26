// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

import static java.util.Collections.singletonMap;

public final class JavaAutoModuleNameIndex extends ScalarIndexExtension<String> {
  private static final ID<String, Void> NAME = ID.create("java.auto.module.name");

  private final FileBasedIndex.InputFilter myFilter = new DefaultFileTypeSpecificInputFilter(ArchiveFileType.INSTANCE) {
    @Override
    public boolean acceptInput(@NotNull VirtualFile f) {
      return f.isDirectory() && f.getParent() == null && "jar".equalsIgnoreCase(f.getExtension());
    }
  };

  private final DataIndexer<String, Void, FileContent> myIndexer = data -> singletonMap(LightJavaModule.moduleName(FileUtilRt.getNameWithoutExtension(data.getFileName())), null);

  @Override
  public boolean indexDirectories() {
    return true;
  }

  @Override
  public @NotNull ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public int getVersion() {
    return 6;
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return myFilter;
  }

  @Override
  public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
    return myIndexer;
  }

  @Override
  public @NotNull Collection<FileType> getFileTypesWithSizeLimitNotApplicable() {
    return Collections.singleton(JavaClassFileType.INSTANCE);
  }

  public static @NotNull Collection<VirtualFile> getFilesByKey(@NotNull String moduleName, @NotNull GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, moduleName, new JavaAutoModuleFilterScope(scope));
  }

  public static @NotNull Collection<String> getAllKeys(@NotNull Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }
}
