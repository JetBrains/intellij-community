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
package com.intellij.java.codeInsight.completion
import com.intellij.JavaTestUtil
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.testFramework.NeedsIndex
import groovy.transform.CompileStatic

@CompileStatic
class SmartTypeCompletionDfaTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/smartType/"
  }

  @Override
  protected void complete() {
    myItems = myFixture.complete(CompletionType.SMART)
  }

  private void doTest() {
    configureByTestName()
    checkResultByTestName()
  }

  private void checkResultByTestName() {
    checkResultByFile("/" + getTestName(false) + "-out.java")
  }

  @NeedsIndex.Full
  void testCastGenericQualifier() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testDontAutoCastWhenAlreadyCasted() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems(0, "s", "toString")
    myFixture.type('\n')
    checkResultByTestName()
  }

  @NeedsIndex.ForStandardLibrary
  void testAutoCastWhenAlreadyCasted() {
    configureByTestName()
    myFixture.type('\n')
    checkResultByTestName()
  }

  @NeedsIndex.ForStandardLibrary
  void testSuggestCastedValueAfterCast() { doTest() }

  @NeedsIndex.Full
  void testSuggestInstanceofedValue() { doTest() }

  void testSuggestInstanceofedValueInTernary() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testSuggestInstanceofedValueInComplexIf() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testSuggestInstanceofedValueInElseNegated() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testSuggestInstanceofedValueAfterReturn() { doTest() }

  void testNoInstanceofedValueWhenBasicSuits() { doTest() }

  void testNoInstanceofedValueInElse() { doAntiTest() }

  void testNoInstanceofedValueInThenNegated() { doAntiTest() }

  void testNoInstanceofedValueInElseWithComplexIf() { doAntiTest() }

  void testInstanceofedInsideAnonymous() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testCastToTypeWithWildcard() { doTest() }

}
