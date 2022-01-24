// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.storage.MapReduceIndexBase;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

@ApiStatus.Internal
public abstract class StubUpdatableIndexFactory {
  static StubUpdatableIndexFactory getInstance() {
    return ApplicationManager.getApplication().getService(StubUpdatableIndexFactory.class);
  }

  @NotNull
  public abstract MapReduceIndexBase<Integer, SerializedStubTree> createIndex(@NotNull FileBasedIndexExtension<Integer, SerializedStubTree> extension,
                                                                              @NotNull VfsAwareIndexStorageLayout<Integer, SerializedStubTree> layout,
                                                                              @NotNull SerializationManagerEx serializationManager)
    throws IOException;
}
