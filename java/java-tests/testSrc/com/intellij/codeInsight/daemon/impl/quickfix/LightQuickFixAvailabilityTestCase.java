// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * tests corresponding intention for availability only, does not invoke action
 */
public abstract class LightQuickFixAvailabilityTestCase extends LightQuickFixParameterizedTestCase {
  @Override
  protected void doAction(@NotNull final ActionHint actionHint, @NotNull final String testFullPath, @NotNull final String testName) {
    findActionAndCheck(actionHint, testFullPath);
  }
}
