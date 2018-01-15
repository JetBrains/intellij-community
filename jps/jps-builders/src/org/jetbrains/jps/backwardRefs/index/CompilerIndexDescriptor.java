// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.index;

import com.intellij.util.indexing.IndexExtension;

import java.io.File;
import java.util.Collection;

/**
 * Provides access to information about underlying indices in a {@link CompilerReferenceIndex} instance.
 */
public interface CompilerIndexDescriptor<Input> {
  Collection<IndexExtension<?, ?, Input>> getIndices();
  int getVersion();
  File getIndicesDir(File buildDir);
  File getVersionFile(File buildDir);
}
