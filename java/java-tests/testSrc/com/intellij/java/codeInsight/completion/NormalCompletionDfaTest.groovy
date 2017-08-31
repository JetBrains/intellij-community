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
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase

/**
 * @author peter
 */
class NormalCompletionDfaTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/"
  }
  
  void testCastInstanceofedQualifier() { doTest() }
  void testCastInstanceofedQualifierInForeach() { doTest() }
  void testCastComplexInstanceofedQualifier() { doTest() }
  void _testCastIncompleteInstanceofedQualifier() { doTest() }

  void testCastTooComplexInstanceofedQualifier() { doAntiTest() }

  void testCastInstanceofedThisQualifier() { doTest() }

  void testDontCastInstanceofedQualifier() { doTest() }
  void testDontCastPartiallyInstanceofedQualifier() { doAntiTest() }
  void testQualifierCastingWithUnknownAssignments() { doTest() }
  void testQualifierCastingBeforeLt() { doTest() }
  void testCastQualifierForPrivateFieldReference() { doTest() }
  void testOrAssignmentDfa() { doTest() }
  void testFieldWithCastingCaret() { doTest() }

  void testCastTwice() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'b', 'a'
  }

  void testPublicMethodExtendsProtected() {
    myFixture.addClass '''
package foo;
public class Foo {
    protected void consume() {}
}

'''
    myFixture.addClass '''
package foo;
public class FooImpl extends Foo {
    public void consume() {}
}
'''
    doTest()
  }

  private void doTest() throws Exception {
    configureByTestName()
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void testCastInstanceofedQualifierInLambda() { doTest() }

  void testCastInstanceofedQualifierInLambda2() { doTest() }

  void testCastInstanceofedQualifierInExpressionLambda() { doTest() }

  void testPreferCastExpressionSuperTypes() {
    myFixture.addClass('package nonImported; public interface SzNameInTheEnd {}')
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'SString', 'SzNameInTheEnd', 'Serializable'
  }

}
