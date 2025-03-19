// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.migration;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.psi.PsiKeyword;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.migration.IfCanBeSwitchInspection;

/**
 * @author Bas Leijdekkers
 */
public class IfCanBeSwitchFixTest extends IGQuickFixesTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    final IfCanBeSwitchInspection inspection = new IfCanBeSwitchInspection();
    inspection.minimumBranches = 2;
    inspection.suggestIntSwitches = true;
    myFixture.enableInspections(inspection);
    myRelativePath = "migration/if_can_be_switch";
    myDefaultHint = CommonQuickFixBundle.message("fix.replace.x.with.y", PsiKeyword.IF, PsiKeyword.SWITCH);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }

  public void testComment() { doTest();}
  public void testComment2() { doTest();}
  public void testComments() { doTest(); }
  public void testLong() { assertQuickfixNotAvailable(); }
  public void testPolyadic() { doTest(); }
  public void testStringEquality() { doTest(); }
  public void testObjectsEquals() { doTest(); }
  public void testEnumEquals() { doTest(); }
  public void testRelabelingBreaks() { doTest(); }
  public void testIfWithoutThenBranch() { doTest(); }
  public void testIfWithIsEmpty() { doTest(); }
  public void testIfWithIsEmptyInFirst() { doTest(); }
}
