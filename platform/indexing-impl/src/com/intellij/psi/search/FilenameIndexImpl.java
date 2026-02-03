// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.util.indexing.*;
import com.intellij.util.indexing.hints.AcceptAllFilesAndDirectoriesIndexingHint;
import com.intellij.util.indexing.hints.RejectAllIndexingHint;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

final class FilenameIndexImpl extends ScalarIndexExtension<String> {
  @Override
  public @NotNull ID<String,Void> getName() {
    return FilenameIndex.NAME;
  }

  @Override
  public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
    return inputData -> Collections.singletonMap(inputData.getFileName(), null);
  }

  @Override
  public @NotNull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    if (FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX) {
      return RejectAllIndexingHint.INSTANCE;
    }
    else {
      return AcceptAllFilesAndDirectoriesIndexingHint.INSTANCE;
    }
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
    return 3 + (FileBasedIndexExtension.USE_VFS_FOR_FILENAME_INDEX ? 0xff : 0);
  }

  @Override
  public boolean traceKeyHashToVirtualFileMapping() {
    return true;
  }
}
