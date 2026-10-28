// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.navigation;

import com.intellij.testFramework.TestIndexingModeSupporter;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Runs {@link ChooseByNameTest} with the legacy based goto providers. Such a provider wraps a legacy contributor.
 * <p>
 * The class runs only in the smart mode, because {@link TestIndexingModeSupporter} reads only the test methods that a class declares.
 */
@SuppressWarnings("NewClassNamingConvention")
public class ChooseByNameLegacyBasedGotoTest extends ChooseByNameTest {
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static Test suite() {
    return new TestSuite(ChooseByNameLegacyBasedGotoTest.class);
  }

  @Override
  protected boolean isCoroutineBasedGoto() {
    return false;
  }
}
