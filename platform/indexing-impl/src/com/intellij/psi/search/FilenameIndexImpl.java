// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.search;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.indexing.*;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class FilenameIndexImpl extends ScalarIndexExtension<String> {
  @NotNull
  @Override
  public ID<String,Void> getName() {
    return FilenameIndex.NAME;
  }

  @NotNull
  @Override
  public DataIndexer<String, Void, FileContent> getIndexer() {
    return inputData -> Collections.singletonMap(inputData.getFileName(), null);
  }

  @NotNull
  @Override
  public KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return file -> true;
  }

  @Override
  public boolean dependsOnFileContent() {
    return false;
  }

  @Override
  public boolean indexDirectories() {
    return true;
  }

  @Override
  public int getVersion() {
    return 3 + (Registry.is("indexing.filename.over.vfs") ? 0xff : 0);
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return true;
  }
}
