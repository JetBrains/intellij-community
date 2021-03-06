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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.miscGenerics.RawUseOfParameterizedTypeInspection;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.DefaultLogger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RawTypeCanBeGenericFixTest extends LightJavaCodeInsightFixtureTestCase {
  private static final ProjectDescriptor JDK_8_WITH_LEVEL_6 = new ProjectDescriptor(LanguageLevel.JDK_1_6) {
    @Override
    public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model, @NotNull ContentEntry contentEntry) {
      super.configureModule(module, model, contentEntry);
      model.setSdk(IdeaTestUtil.getMockJdk18());
    }
  };

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInspection/makeTypeGeneric";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new RawUseOfParameterizedTypeInspection());
  }

  public void testField() {
    doTest(getMessage("TT", "Comparator<String>"));
  }

  public void testLocalVariable() {
    doTest(getMessage("list", "List<String>"));
  }

  public void testAvoidUnrelatedWarnings() {
    doTest(getMessage("iterator", "Iterator<String>"));
  }

  public void testAtEquals() {
    doTest(getMessage("list", "List<String>"));
  }

  public void testConflict() {
    DefaultLogger.disableStderrDumping(getTestRootDisposable());
    try {
      doTest(getMessage("list", "List<T>"));
      fail("No conflict detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Cannot convert type of expression <b>&quot;&quot;</b> from <b>java.lang.String</b> to <b>T</b><br>", 
                   e.getMessage());
    }
  }

  public void testAtInitializer() {
    assertIntentionNotAvailable(getMessagePrefix());
  }

  public void testImplementedRaw() {
    assertIntentionNotAvailable(getMessagePrefix());
  }

  public void testBoundedTypeParameter() {
    doTest(getMessage("list", "List<? extends Integer>"));
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
    return JavaBundle.message("raw.variable.type.can.be.generic.quickfix", variable, type);
  }

  private static String getMessagePrefix() {
    String message = JavaBundle.message("raw.variable.type.can.be.generic.quickfix", "@", "@");
    return message.substring(0, message.indexOf("@"));
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JDK_8_WITH_LEVEL_6;
  }
}
