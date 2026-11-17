// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation;

import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModes;
import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode;

/**
 * Runs {@link ChooseByNameTest} with the legacy based goto providers. Such a provider wraps a legacy contributor.
 * <p>
 * The class runs only in the smart mode.
 */
@IndexingModes(IndexingMode.SMART)
public class ChooseByNameLegacyBasedGotoTest extends ChooseByNameTest {
  @Override
  protected boolean isCoroutineBasedGoto() {
    return false;
  }
}
