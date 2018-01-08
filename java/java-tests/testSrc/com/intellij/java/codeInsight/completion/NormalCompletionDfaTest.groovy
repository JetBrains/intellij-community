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

import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull

/**
 * @author peter
 */
class NormalCompletionDfaTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8
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
  void testAssignmentPreciseTypeDfa() { doTest() }
  void testDeclarationPreciseTypeDfa() { doTest() }
  void testInstanceOfAssignmentDfa() { doTest() }
  void testStreamDfa() { doTest() }
  void testStreamIncompleteDfa() { doTest() }
  void testOptionalDfa() { doTest() }
  void testFieldWithCastingCaret() { doTest() }
  void testCastWhenMethodComesFromDfaSuperType() { doTest() }
  void testGenericTypeDfa() { doTest() }
  void testNoUnnecessaryCastDfa() { doTest() }
  void testNoUnnecessaryCastRawDfa() { doTest() }
  void testInstanceOfAfterFunction() { doTest() }
  void testComplexInstanceOfDfa() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'methodFromX', 'methodFromX2', 'methodFromY', 'methodFromY2'
  }

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

  void testCastInstanceofedQualifierInLambda() { doTest() }

  void testCastInstanceofedQualifierInLambda2() { doTest() }

  void testCastInstanceofedQualifierInExpressionLambda() { doTest() }
  
  void testCastQualifierInstanceofedTwice() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'boo', 'foo', 'moo'
    myFixture.lookup.currentItem = myFixture.lookupElements[1]
    myFixture.type('\n')
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void testPreferCastExpressionSuperTypes() {
    myFixture.addClass('package nonImported; public interface SzNameInTheEnd {}')
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'SString', 'SzNameInTheEnd', 'Serializable'
  }

}
