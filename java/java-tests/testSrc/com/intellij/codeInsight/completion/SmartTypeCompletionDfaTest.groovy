/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion
import com.intellij.JavaTestUtil
/**
 * @author peter
 */
class SmartTypeCompletionDfaTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/smartType/";
  }

  @Override
  protected void complete() {
    myItems = myFixture.complete(CompletionType.SMART);
  }

  private void doTest() {
    configureByTestName();
    checkResultByTestName();
  }

  private void checkResultByTestName() {
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  void testCastGenericQualifier() { doTest() }

  void testDontAutoCastWhenAlreadyCasted() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "s", "toString");
    myFixture.type('\n')
    checkResultByTestName();
  }

  void testAutoCastWhenAlreadyCasted() {
    configureByTestName();
    myFixture.type('\n')
    checkResultByTestName();
  }

  void testSuggestCastedValueAfterCast() { doTest(); }

  void testSuggestInstanceofedValue() { doTest() }

  void testSuggestInstanceofedValueInTernary() { doTest() }

  void testSuggestInstanceofedValueInComplexIf() { doTest(); }

  void testSuggestInstanceofedValueInElseNegated() { doTest(); }

  void testSuggestInstanceofedValueAfterReturn() { doTest(); }

  void testNoInstanceofedValueWhenBasicSuits() { doTest(); }

  void testNoInstanceofedValueInElse() { doAntiTest(); }

  void testNoInstanceofedValueInThenNegated() { doAntiTest(); }

  void testNoInstanceofedValueInElseWithComplexIf() { doAntiTest(); }

  void testInstanceofedInsideAnonymous() { doTest(); }

}
