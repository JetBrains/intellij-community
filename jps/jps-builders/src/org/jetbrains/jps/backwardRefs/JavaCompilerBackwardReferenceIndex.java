// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;
import org.jetbrains.jps.backwardRefs.index.JavaCompilerIndices;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;

import java.io.File;

public final class JavaCompilerBackwardReferenceIndex extends CompilerReferenceIndex<CompiledFileData> {
  public JavaCompilerBackwardReferenceIndex(File buildDir, PathRelativizerService relativizer, boolean readOnly) {
    super(JavaCompilerIndices.getIndices(), buildDir, relativizer, readOnly, JavaCompilerIndices.VERSION);
  }
}
