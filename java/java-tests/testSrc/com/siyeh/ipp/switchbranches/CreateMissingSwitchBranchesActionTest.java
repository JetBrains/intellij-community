// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.switchbranches;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.psiutils.CreateSwitchBranchesUtil;
import com.siyeh.ipp.IPPFixture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;

@TestApplication
public class CreateMissingSwitchBranchesActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "switchbranches");

  @Test
  public void testDataflowInt() {
    ipp.get().doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("1", "3", "5", "7", "9")));
  }
  
  @Test
  public void testDataflowLong() {
    ipp.get().assertIntentionNotAvailable(CreateSwitchBranchesUtil.getActionName(Arrays.asList("1", "2")));
  }

  @Test
  public void testDataflowIntMixedTypes() {
    ipp.get().doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("0", "1", "3", "4", "6", "7", "9")));
  }

  @Test
  public void testDataflowChar() {
    ipp.get().doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("a", "c", "d", "e", "h", "i", "j", "k", "l", "m", "n")));
  }

  @Test
  public void testDataflowString() {
    ipp.get().doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("\"bar\"", "\"baz\"", "\"foo\"")));
  }

  @Test
  public void testMagicInt() {
    ipp.get().doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("VAL1", "VAL3")));
  }

  @Test
  public void testMagicIntMethodCall() {
    ipp.get().doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("TWO", "-1")));
  }

  @Test
  public void testMagicIntFlags() {
    ipp.get().assertIntentionNotAvailable(CreateSwitchBranchesUtil.getActionName(Arrays.asList("VAL1", "VAL3")));
  }

  @Test
  public void testMagicString() {
    ipp.get().doTest(CreateSwitchBranchesUtil.getActionName(Arrays.asList("FOO", "BAZ")));
  }
}
