// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.util.indexing.roots.kind.ModuleRootOrigin;
import org.jetbrains.annotations.NotNull;

public interface ModuleIndexableFilesIterator extends IndexableFilesIterator {
  @Override
  @NotNull ModuleRootOrigin getOrigin();
}
