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

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.LightProjectDescriptor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
/**
 * @author peter
 */
@CompileStatic
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
  void testAssignmentPreciseTypeDfa() { doTestSecond() }
  void testAssignmentTwicePreciseTypeDfa() { doTestSecond() }
  void testAssignmentParameterDfa() { doTest() }
  void testAssignmentNoPreciseTypeDfa() { doTest() }
  void testAssignmentPrimitiveLiteral() { doTest() }
  void testDeclarationPreciseTypeDfa() { doTestSecond() }
  void testInstanceOfAssignmentDfa() { doTestSecond() }
  void testStreamDfa() { doTest() }
  void testStreamIncompleteDfa() { doTest() }
  void testOptionalDfa() { doTest() }
  void testFieldWithCastingCaret() { doTest() }
  void testCastWhenMethodComesFromDfaSuperType() { doTest() }
  void testGenericTypeDfa() { doTestSecond() }
  void testNarrowingReturnType() { doTest() }
  void testNarrowingReturnTypeInVoidContext() { doTest() }
  void testNoUnnecessaryCastDfa() { doTest() }
  void testNoUnnecessaryCastRawDfa() { doTest() }
  void testInconsistentHierarchyDfa() { doTest() }
  void testAfterAssertTrueDfa() { doTest() }
  void testNoUnnecessaryCastDeepHierarchy() { doTest() }
  void testInstanceOfAfterFunction() { doTest() }
  void testInstanceOfDisjunction() { doTest() }
  void testInstanceOfDisjunction2() { doTest() }
  void testInstanceOfDisjunctionDeep() { doTest() }
  void testInstanceOfDisjunctionCircular() { doTest() }
  void testAfterGetClass() { doTest() }
  void testNoCastForCompatibleCapture() { doTest() }
  void testComplexInstanceOfDfa() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'methodFromX', 'methodFromX2', 'methodFromY', 'methodFromY2'

    assert LookupElementPresentation.renderElement(myItems[0]).tailText == '() on X'
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

  void 'test no casts to inaccessible type'() {
    myFixture.addClass '''
package some;

public abstract class PublicInterface {

    public static PackagePrivateImplementation createPrivateImplementation() {
        return new PackagePrivateImplementation();
    }
}

class PackagePrivateImplementation extends PublicInterface {
    public String getValue() { }
}
'''

    myFixture.configureByText 'a.java', '''
import some.PublicInterface;

public class Main {

    public static void main(String[] args) {
        PublicInterface i = PublicInterface.createPrivateImplementation();
        i.getVa<caret>x
    }
}'''

    assert myFixture.complete(CompletionType.BASIC, 2).size() == 0
  }

  void 'test show methods accessible on base type but inaccessible on cast type'() {
    def clazz = myFixture.addClass '''
package some;
import another.*;

public class Super {
    void foo() {}
    
    void test(Super o) {
      if (o instanceof Sub) {
        o.fo<caret>o
      }
    }
}

'''
    myFixture.addClass 'package another; public class Sub extends some.Super {}'

    myFixture.configureFromExistingVirtualFile(clazz.containingFile.virtualFile)

    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['foo']
  }

  private void doTestSecond() {
    configure()
    assert myItems == null || myItems.length == 0
    myFixture.completeBasic()
    checkResult()
  }
}
