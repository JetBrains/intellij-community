// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Tests for {@link com.intellij.codeInspection.DuplicateBranchesInSwitchInspection}
 */
@Ignore("Manual only")
@RunWith(Suite.class)
@Suite.SuiteClasses({
  DuplicateBranchesInEnhancedSwitchTest.class,
  DuplicateBranchesInSwitchTest.class,
})
public class DuplicateBranchesInSwitchSuite {
}
