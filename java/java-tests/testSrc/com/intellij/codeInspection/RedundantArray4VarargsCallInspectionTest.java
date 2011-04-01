/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.*;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;

/**
 * User: anna
 * Date: 11/13/10
 */
public class RedundantArray4VarargsCallInspectionTest extends UsefulTestCase {
   protected CodeInsightTestFixture myFixture;
  private RedundantArrayForVarargsCallInspection myInspection;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(new DefaultLightProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture,
                                                                                    new LightTempDirTestFixtureImpl(true));
    myInspection = new RedundantArrayForVarargsCallInspection();
    myFixture.enableInspections(myInspection);
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    myInspection = null;
    super.tearDown();
  }

  public void testPreserveComments() {
    doTest();
  }

  public void testRemoveTailingCommas() {
    doTest();
  }

  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.launchAction(assertOneElement(myFixture.filterAvailableIntentions(InspectionsBundle.message("inspection.redundant.array.creation.quickfix"))));
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }


  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantArrayForVarargs/quickFix";
  }

}