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
import com.intellij.testFramework.NeedsIndicesState
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

  @NeedsIndicesState.StandardLibraryIndices
  void testCastInstanceofedQualifier() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testCastInstanceofedQualifierInForeach() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testCastComplexInstanceofedQualifier() { doTest() }
  void _testCastIncompleteInstanceofedQualifier() { doTest() }

  void testCastTooComplexInstanceofedQualifier() { doAntiTest() }

  void testCastInstanceofedThisQualifier() { doTest() }

  @NeedsIndicesState.StandardLibraryIndices
  void testDontCastInstanceofedQualifier() { doTest() }
  void testDontCastPartiallyInstanceofedQualifier() { doAntiTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testQualifierCastingWithUnknownAssignments() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testQualifierCastingBeforeLt() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testCastQualifierForPrivateFieldReference() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testOrAssignmentDfa() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testAssignmentPreciseTypeDfa() { doTestSecond() }
  @NeedsIndicesState.StandardLibraryIndices
  void testAssignmentTwicePreciseTypeDfa() { doTestSecond() }
  void testAssignmentParameterDfa() { doTest() }
  void testAssignmentNoPreciseTypeDfa() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testAssignmentPrimitiveLiteral() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testDeclarationPreciseTypeDfa() { doTestSecond() }
  @NeedsIndicesState.StandardLibraryIndices
  void testInstanceOfAssignmentDfa() { doTestSecond() }
  @NeedsIndicesState.StandardLibraryIndices
  void testStreamDfa() { doTest() }
  @NeedsIndicesState.FullIndices
  void testStreamIncompleteDfa() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testOptionalDfa() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testFieldWithCastingCaret() { doTest() }
  void testCastWhenMethodComesFromDfaSuperType() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testGenericTypeDfa() { doTestSecond() }
  void testNarrowingReturnType() { doTest() }
  void testNarrowingReturnTypeInVoidContext() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testNoUnnecessaryCastDfa() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testNoUnnecessaryCastRawDfa() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testInconsistentHierarchyDfa() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testAfterAssertTrueDfa() { doTest() }
  void testNoUnnecessaryCastDeepHierarchy() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testInstanceOfAfterFunction() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testInstanceOfDisjunction() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testInstanceOfDisjunction2() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testInstanceOfDisjunctionDeep() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testInstanceOfDisjunctionCircular() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testAfterGetClass() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testNoCastForCompatibleCapture() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testBooleanFlagDfa() { doTest() }
  @NeedsIndicesState.StandardLibraryIndices
  void testComplexInstanceOfDfa() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'methodFromX', 'methodFromX2', 'methodFromY', 'methodFromY2'

    assert LookupElementPresentation.renderElement(myItems[0]).tailText == '() on X'
  }

  @NeedsIndicesState.StandardLibraryIndices
  void testCastTwice() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'b', 'a'
  }

  @NeedsIndicesState.FullIndices
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

  @NeedsIndicesState.StandardLibraryIndices
  void testCastInstanceofedQualifierInLambda() { doTest() }

  @NeedsIndicesState.StandardLibraryIndices
  void testCastInstanceofedQualifierInLambda2() { doTest() }

  @NeedsIndicesState.StandardLibraryIndices
  void testCastInstanceofedQualifierInExpressionLambda() { doTest() }
  
  void testCastQualifierInstanceofedTwice() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'boo', 'foo', 'moo'
    myFixture.lookup.currentItem = myFixture.lookupElements[1]
    myFixture.type('\n')
    checkResultByFile(getTestName(false) + "_after.java")
  }

  @NeedsIndicesState.FullIndices
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
