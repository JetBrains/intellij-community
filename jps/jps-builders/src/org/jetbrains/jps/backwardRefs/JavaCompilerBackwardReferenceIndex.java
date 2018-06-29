// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import org.jetbrains.jps.backwardRefs.index.CompiledFileData;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;
import org.jetbrains.jps.backwardRefs.index.JavaCompilerIndices;

import java.io.File;

public class JavaCompilerBackwardReferenceIndex extends CompilerReferenceIndex<CompiledFileData> {
  public JavaCompilerBackwardReferenceIndex(File buildDir, boolean readOnly) {
    super(JavaCompilerIndices.getIndices(), buildDir, readOnly, JavaCompilerIndices.VERSION);
  }
}
