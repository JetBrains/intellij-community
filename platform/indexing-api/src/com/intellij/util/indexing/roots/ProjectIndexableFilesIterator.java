// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.roots;

import com.intellij.util.indexing.roots.kind.ProjectFileOrDirOrigin;
import org.jetbrains.annotations.NotNull;

public interface ProjectIndexableFilesIterator extends IndexableFilesIterator {
  @Override
  @NotNull ProjectFileOrDirOrigin getOrigin();
}
