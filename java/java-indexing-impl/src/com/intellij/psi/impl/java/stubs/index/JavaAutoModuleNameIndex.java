/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.light.LightJavaModule;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static java.util.Collections.singletonMap;

public class JavaAutoModuleNameIndex extends ScalarIndexExtension<String> {
  private static final ID<String, Void> NAME = ID.create("java.auto.module.name");

  private final FileBasedIndex.InputFilter myFilter =
    file -> file.isDirectory() && file.getParent() == null && "jar".equalsIgnoreCase(file.getExtension());

  private final DataIndexer<String, Void, FileContent> myIndexer =
    data -> singletonMap(LightJavaModule.moduleName(data.getFile()), null);

  @NotNull
  @Override
  public ID<String, Void> getName() {
    return NAME;
  }

  @Override
  public int getVersion() {
    return 1 + (FileBasedIndex.ourEnableTracingOfKeyHashToVirtualFileMapping ? 2 : 0);
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
}