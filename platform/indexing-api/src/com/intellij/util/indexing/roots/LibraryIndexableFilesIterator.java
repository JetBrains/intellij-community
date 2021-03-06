// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.util.indexing.roots.kind.LibraryOrigin;
import org.jetbrains.annotations.NotNull;

public interface LibraryIndexableFilesIterator extends IndexableFilesIterator {
  @Override
  @NotNull LibraryOrigin getOrigin();
}
