// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.backwardRefs;

import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;
import org.jetbrains.jps.backwardRefs.index.JavaCompilerIndices;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.nio.file.Path;

public final class JavaCompilerBackwardReferenceIndex extends CompilerReferenceIndex<CompiledFileData> {
  public JavaCompilerBackwardReferenceIndex(Path buildDir, PathRelativizerService relativizer, boolean readOnly) {
    super(JavaCompilerIndices.getIndices(), buildDir, relativizer, readOnly, JavaCompilerIndices.VERSION);
  }

  public JavaCompilerBackwardReferenceIndex(Path buildDir, PathRelativizerService relativizer, boolean readOnly, boolean isCaseSensitive) {
    super(JavaCompilerIndices.getIndices(), buildDir, relativizer, readOnly, JavaCompilerIndices.VERSION, isCaseSensitive);
  }
}
