// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import com.intellij.util.indexing.IndexExtension;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.backwardRefs.index.CompilerIndexDescriptor;
import org.jetbrains.jps.backwardRefs.index.CompilerIndices;

import java.io.File;
import java.util.Collection;

public class BackwardReferenceIndexDescriptor implements CompilerIndexDescriptor<CompiledFileData> {
  public static final BackwardReferenceIndexDescriptor INSTANCE = new BackwardReferenceIndexDescriptor();

  private static final String VERSION_FILE = "version";
  private static final String INDEX_DIR = "backward-refs";

  @Override
  public Collection<IndexExtension<?, ?, CompiledFileData>> getIndices() {
    return CompilerIndices.getIndices();
  }

  @Override
  public int getVersion() {
    return CompilerIndices.VERSION;
  }

  @Override
  public File getIndicesDir(File buildDir) {
    return new File(buildDir, INDEX_DIR);
  }

  @Override
  public File getVersionFile(File buildDir) {
    return new File(buildDir, VERSION_FILE);
  }
}
