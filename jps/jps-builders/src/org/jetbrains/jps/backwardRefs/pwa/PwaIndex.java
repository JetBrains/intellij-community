// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.backwardRefs.pwa;

import com.intellij.util.indexing.IndexExtension;
import org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex;

import java.io.*;
import java.util.Collection;

public class PwaIndex extends CompilerReferenceIndex<ClassFileData> {
  public PwaIndex(File buildDir,
                  boolean readOnly) {
    super(PwaIndices.getIndices(), buildDir, readOnly, PwaIndices.VERSION);
  }
}
