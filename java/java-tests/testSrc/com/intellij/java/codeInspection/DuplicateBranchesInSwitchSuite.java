// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Tests for {@link com.intellij.codeInspection.DuplicateBranchesInSwitchInspection}
 * @author Pavel.Dolgov
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
  DuplicateBranchesInEnhancedSwitchFixTest.class,
  DuplicateBranchesInEnhancedSwitchTest.class,
  DuplicateBranchesInSwitchFixTest.class,
  DuplicateBranchesInSwitchTest.class
})
public class DuplicateBranchesInSwitchSuite {
}
