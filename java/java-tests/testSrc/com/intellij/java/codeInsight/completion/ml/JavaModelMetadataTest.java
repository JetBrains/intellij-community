// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion.ml;

import com.intellij.codeInsight.completion.ml.JavaMLRankingProvider;
import org.junit.Test;

public class JavaModelMetadataTest {
  @Test
  public void testMetadataConsistent() {
    new JavaMLRankingProvider().assertModelMetadataConsistent();
  }
}
