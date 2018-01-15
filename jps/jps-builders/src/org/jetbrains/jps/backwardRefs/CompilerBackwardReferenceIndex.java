// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs;

import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;
import org.jetbrains.jps.backwardRefs.index.CompiledFileData;

import java.io.File;

public class CompilerBackwardReferenceIndex extends CompilerReferenceIndex<CompiledFileData> {
  public CompilerBackwardReferenceIndex(File buildDir, boolean readOnly) {
    super(BackwardReferenceIndexDescriptor.INSTANCE, buildDir, readOnly);
  }
}
