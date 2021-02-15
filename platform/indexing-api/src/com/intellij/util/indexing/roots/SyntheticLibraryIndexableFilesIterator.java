// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.util.indexing.roots.kind.SyntheticLibraryOrigin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface SyntheticLibraryIndexableFilesIterator extends IndexableFilesIterator {
  @Override
  @NotNull SyntheticLibraryOrigin getOrigin();
}
