// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Tests for {@link com.intellij.codeInspection.DuplicateBranchesInSwitchInspection}
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  DuplicateBranchesInEnhancedSwitchFixTest.class,
  DuplicateBranchesInEnhancedSwitchTest.class,
  DuplicateBranchesInSwitchFixTest.class,
  DuplicateBranchesInSwitchTest.class,
  DuplicateBranchesInEnhancedSwitchFix21PreviewTest.class,
})
public class DuplicateBranchesInSwitchSuite {
}
