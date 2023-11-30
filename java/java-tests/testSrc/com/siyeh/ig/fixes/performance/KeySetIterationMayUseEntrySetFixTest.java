/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.performance.KeySetIterationMayUseEntrySetInspection;

public class KeySetIterationMayUseEntrySetFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new KeySetIterationMayUseEntrySetInspection());
    myRelativePath = "performance/key_set_with_entry_set";
    myDefaultHint = CommonQuickFixBundle.message("fix.replace.with.x", "entrySet()");
  }
  
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder builder) throws Exception {
    super.tuneFixture(builder);
    builder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
    builder.setLanguageLevel(LanguageLevel.JDK_1_8);
  }

  public void testSimple() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  public void testParentheses() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  public void testParenthesesAroundGetKey() { doTest(); }
  public void testCastNeeded1() { doTest(); }
  public void testCastNeeded2() { doTest(); }
  public void testReference() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  public void testValues() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  public void testSeveralKeys() { doTest(); }
  public void testWrongType() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  public void testEntryIterationBug() { doTest(); }
  public void testLambda() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Map.forEach()")); }
  public void testLambdaNoVar() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "Map.forEach()")); }
  public void testWildcardType() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "values()")); }
  public void testNestedLambdaNameConflict() { doTest(CommonQuickFixBundle.message("fix.replace.with.x", "entrySet()")); }

}