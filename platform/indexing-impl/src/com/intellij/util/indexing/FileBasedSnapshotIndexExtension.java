// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface FileBasedSnapshotIndexExtension extends SnapshotIndexExtension<FileContent> {
  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  default HashContributor<FileContent> getHashContributor() {
    return FileContentHashContributor.create((FileBasedIndexExtension)this);
  }
}
