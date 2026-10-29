// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation;

/**
 * Runs {@link ChooseByNameHddTest} with the legacy based goto providers. Such a provider wraps a legacy contributor.
 */
public class ChooseByNameHddLegacyBasedGotoTest extends ChooseByNameHddTest {
  @Override
  protected boolean isCoroutineBasedGoto() {
    return false;
  }
}
