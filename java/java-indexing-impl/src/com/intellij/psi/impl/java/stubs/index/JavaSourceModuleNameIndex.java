// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.jar.Manifest;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

public class JavaSourceModuleNameIndex extends ScalarIndexExtension<String> {
  private static final ID<String, Void> NAME = ID.create("java.source.module.name");

  private final FileType myManifestFileType = FileTypeRegistry.getInstance().getFileTypeByExtension("MF");
  private final FileBasedIndex.InputFilter myFilter = new DefaultFileTypeSpecificInputFilter(myManifestFileType) {
    @Override
    public boolean acceptInput(@NotNull VirtualFile f) {
      return f.isInLocalFileSystem();
    }
  };

  private final DataIndexer<String, Void, FileContent> myIndexer = data -> {
    try {
      String name = new Manifest(new ByteArrayInputStream(data.getContent())).getMainAttributes().getValue(PsiJavaModule.AUTO_MODULE_NAME);
      if (name != null) return singletonMap(name, null);
    }
    catch (IOException ignored) { }
    return emptyMap();
  };

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
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
  @Override
  public Collection<FileType> getFileTypesWithSizeLimitNotApplicable() {
    return Collections.singleton(JavaClassFileType.INSTANCE);
  }

  @NotNull
  public static Collection<VirtualFile> getFilesByKey(@NotNull String moduleName, @NotNull GlobalSearchScope scope) {
    return FileBasedIndex.getInstance().getContainingFiles(NAME, moduleName, new JavaAutoModuleFilterScope(scope));
  }

  @NotNull
  public static Collection<String> getAllKeys(@NotNull Project project) {
    return FileBasedIndex.getInstance().getAllKeys(NAME, project);
  }
}