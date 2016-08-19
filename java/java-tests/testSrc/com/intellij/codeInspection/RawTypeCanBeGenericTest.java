/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.miscGenerics.RawTypeCanBeGenericInspection;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.List;

public class RawTypeCanBeGenericTest extends LightCodeInsightFixtureTestCase {
  private RawTypeCanBeGenericInspection myInspection = new RawTypeCanBeGenericInspection();

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInspection/makeTypeGeneric";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ModuleRootModificationUtil.setModuleSdk(myModule, IdeaTestUtil.getMockJdk18());
    myFixture.enableInspections(myInspection);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.disableInspections(myInspection);
    }
    finally {
      super.tearDown();
    }
  }

  public void testField() {
    doTest(getMessage("TT", "Comparator<String>"));
  }

  public void testLocalVariable() {
    doTest(getMessage("list", "List<String>"));
  }

  public void testAtEquals() {
    doTest(getMessage("list", "List<String>"));
  }

  public void testAtInitializer() {
    assertIntentionNotAvailable(getMessagePrefix());
  }

  public void testImplementedRaw() {
    assertIntentionNotAvailable(getMessagePrefix());
  }

  private void doTest(String intentionName) {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(intentionName);
    myFixture.launchAction(singleIntention);
    myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", true);
  }

  private void assertIntentionNotAvailable(String intentionName) {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(intentionName);
    assertEmpty(intentionName + " is not expected", intentionActions);
  }

  private static String getMessage(String variable, String type) {
    return InspectionsBundle.message("inspection.raw.variable.type.can.be.generic.quickfix", variable, type);
  }

  private static String getMessagePrefix() {
    String message = InspectionsBundle.message("inspection.raw.variable.type.can.be.generic.quickfix", "@", "@");
    return message.substring(0, message.indexOf("@"));
  }
}
