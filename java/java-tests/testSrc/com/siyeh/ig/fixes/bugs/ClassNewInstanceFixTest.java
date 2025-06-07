// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.bugs.ClassNewInstanceInspection;

/**
 * @author Bas
 */
public class ClassNewInstanceFixTest extends IGQuickFixesTestCase {

  public void testLambda() { doTest(); }
  public void testCreateCatchBlocks() { doTest(); }
  public void testSkipCatchBlocks() { doTest(); }
  public void testAddThrows() { doTest(); }
  public void testBrokenCode() { doTest(); }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new ClassNewInstanceInspection());
    myRelativePath = "bugs/class_new_instance";
    myDefaultHint = CommonQuickFixBundle.message("fix.replace.with.x", "Class.getConstructor().newInstance()");
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
  }
}
