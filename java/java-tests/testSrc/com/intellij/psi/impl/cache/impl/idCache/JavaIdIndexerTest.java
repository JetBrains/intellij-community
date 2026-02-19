// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.ide.highlighter.JavaClassFileType;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.util.ThreeState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaIdIndexerTest {

  @ParameterizedTest(name = "skipSourceIndexing={0}")
  @ValueSource(booleans = {true, false})
  void acceptsJavaSourceFastPath(boolean skipSourceIndexing) {
    JavaIdIndexer indexer = new JavaIdIndexer(skipSourceIndexing);

    assertEquals(
      skipSourceIndexing ? ThreeState.UNSURE : ThreeState.YES,
      indexer.acceptsFileTypeFastPath(JavaFileType.INSTANCE),
      "JavaIdIndexer must index all .java-files if skipSourceFilesInLibraries=false, and must be unsure (depends on file location) otherwise"
    );
  }

  @ParameterizedTest(name = "skipSourceIndexing={0}")
  @ValueSource(booleans = {true, false})
  void acceptsJavaClassFastPath(boolean skipSourceIndexing) {
    JavaIdIndexer indexer = new JavaIdIndexer(skipSourceIndexing);

    assertEquals(
      skipSourceIndexing ? ThreeState.UNSURE : ThreeState.NO,
      indexer.acceptsFileTypeFastPath(JavaClassFileType.INSTANCE),
      "JavaIdIndexer must NOT index .class-files if skipSourceFilesInLibraries=false, and must be unsure (depends on file location) otherwise"
    );
  }
}