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
import com.intellij.testFramework.NeedsIndex
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

  @NeedsIndex.ForStandardLibrary
  void testCastInstanceofedQualifier() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testCastInstanceofedQualifierInForeach() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testCastComplexInstanceofedQualifier() { doTest() }
  void _testCastIncompleteInstanceofedQualifier() { doTest() }

  void testCastTooComplexInstanceofedQualifier() { doAntiTest() }

  void testCastInstanceofedThisQualifier() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testDontCastInstanceofedQualifier() { doTest() }
  void testDontCastPartiallyInstanceofedQualifier() { doAntiTest() }
  @NeedsIndex.ForStandardLibrary
  void testQualifierCastingWithUnknownAssignments() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testQualifierCastingBeforeLt() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testCastQualifierForPrivateFieldReference() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testOrAssignmentDfa() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testAssignmentPreciseTypeDfa() { doTestSecond() }
  @NeedsIndex.ForStandardLibrary
  void testAssignmentTwicePreciseTypeDfa() { doTestSecond() }
  void testAssignmentParameterDfa() { doTest() }
  void testAssignmentNoPreciseTypeDfa() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testAssignmentPrimitiveLiteral() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testDeclarationPreciseTypeDfa() { doTestSecond() }
  @NeedsIndex.ForStandardLibrary
  void testInstanceOfAssignmentDfa() { doTestSecond() }
  @NeedsIndex.ForStandardLibrary
  void testStreamDfa() { doTest() }
  @NeedsIndex.Full
  void testStreamIncompleteDfa() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testOptionalDfa() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testFieldWithCastingCaret() { doTest() }
  void testCastWhenMethodComesFromDfaSuperType() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testGenericTypeDfa() { doTestSecond() }
  void testNarrowingReturnType() { doTest() }
  void testNarrowingReturnTypeInVoidContext() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testNoUnnecessaryCastDfa() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testNoUnnecessaryCastRawDfa() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testInconsistentHierarchyDfa() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testAfterAssertTrueDfa() { doTest() }
  void testNoUnnecessaryCastDeepHierarchy() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testInstanceOfAfterFunction() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testInstanceOfDisjunction() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testInstanceOfDisjunction2() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testInstanceOfDisjunctionDeep() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testInstanceOfDisjunctionCircular() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testAfterGetClass() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testNoCastForCompatibleCapture() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testBooleanFlagDfa() { doTest() }
  @NeedsIndex.ForStandardLibrary
  void testComplexInstanceOfDfa() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'methodFromX', 'methodFromX2', 'methodFromY', 'methodFromY2'

    assert renderElement(myItems[0]).tailText == '() on X'
  }

  @NeedsIndex.ForStandardLibrary
  void testCastTwice() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'b', 'a'
  }

  @NeedsIndex.Full
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

  @NeedsIndex.ForStandardLibrary
  void testCastInstanceofedQualifierInLambda() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testCastInstanceofedQualifierInLambda2() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testCastInstanceofedQualifierInExpressionLambda() { doTest() }
  
  void testCastQualifierInstanceofedTwice() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'boo', 'foo', 'moo'
    myFixture.lookup.currentItem = myFixture.lookupElements[1]
    myFixture.type('\n')
    checkResultByFile(getTestName(false) + "_after.java")
  }

  @NeedsIndex.Full
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
