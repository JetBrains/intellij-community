package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class NormalCompletionDfaTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @NeedsIndex.ForStandardLibrary
  public void testCastInstanceofedQualifier() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testCastInstanceofedQualifierInForeach() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testCastComplexInstanceofedQualifier() { doTest(); }

  public void _testCastIncompleteInstanceofedQualifier() { doTest(); }

  public void testCastTooComplexInstanceofedQualifier() { doAntiTest(); }

  public void testCastInstanceofedThisQualifier() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testDontCastInstanceofedQualifier() { doTest(); }

  public void testDontCastPartiallyInstanceofedQualifier() { doAntiTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testQualifierCastingWithUnknownAssignments() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testQualifierCastingBeforeLt() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testCastQualifierForPrivateFieldReference() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testOrAssignmentDfa() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testAssignmentPreciseTypeDfa() { doTestSecond(); }

  @NeedsIndex.ForStandardLibrary
  public void testAssignmentTwicePreciseTypeDfa() { doTestSecond(); }

  public void testAssignmentParameterDfa() { doTest(); }

  public void testAssignmentNoPreciseTypeDfa() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testAssignmentPrimitiveLiteral() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testDeclarationPreciseTypeDfa() { doTestSecond(); }

  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfAssignmentDfa() { doTestSecond(); }

  @NeedsIndex.ForStandardLibrary
  public void testStreamDfa() { doTest(); }

  @NeedsIndex.Full
  public void testStreamIncompleteDfa() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testOptionalDfa() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testFieldWithCastingCaret() { doTest(); }

  public void testCastWhenMethodComesFromDfaSuperType() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testGenericTypeDfa() { doTestSecond(); }

  public void testNarrowingReturnType() { doTest(); }

  public void testNarrowingReturnTypeInVoidContext() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testNoUnnecessaryCastDfa() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testNoUnnecessaryCastRawDfa() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testInconsistentHierarchyDfa() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testAfterAssertTrueDfa() { doTest(); }

  public void testNoUnnecessaryCastDeepHierarchy() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfAfterFunction() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfDisjunction() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfDisjunction2() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfDisjunctionDeep() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfDisjunctionCircular() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testAfterGetClass() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testNoCastForCompatibleCapture() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testBooleanFlagDfa() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testComplexInstanceOfDfa() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "methodFromX", "methodFromX2", "methodFromY", "methodFromY2");

    assertEquals("() on X", NormalCompletionTestCase.renderElement(myItems[0]).getTailText());
  }

  @NeedsIndex.ForStandardLibrary
  public void testCastTwice() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "b", "a");
  }

  @NeedsIndex.Full
  public void testPublicMethodExtendsProtected() {
    myFixture.addClass("""


                         package foo;
public class Foo {
    protected void consume() {}
}

""");
    myFixture.addClass("""

package foo;
public class FooImpl extends Foo {
    public void consume() {}
}
""");
    doTest();
  }

@NeedsIndex.ForStandardLibrary public void testCastInstanceofedQualifierInLambda() {doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testCastInstanceofedQualifierInLambda2() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testCastInstanceofedQualifierInExpressionLambda() { doTest(); }

  public void testCastQualifierInstanceofedTwice() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "boo", "foo", "moo");
    myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
    myFixture.type("\n");
    checkResultByFile(getTestName(false) + "_after.java");
  }

  @NeedsIndex.Full
  public void testPreferCastExpressionSuperTypes() {
    myFixture.addClass("package nonImported; public interface SzNameInTheEnd {}");
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "SString", "SzNameInTheEnd", "Serializable");
  }

  public void test_no_casts_to_inaccessible_type() {
    myFixture.addClass("""


                         package some;

public abstract class PublicInterface {

    public static PackagePrivateImplementation createPrivateImplementation() {
        return new PackagePrivateImplementation();
    }
}

class PackagePrivateImplementation extends PublicInterface {
    public String getValue() { }
}
""");

    myFixture.configureByText("a.java", """

import some.PublicInterface;

public class Main {

    public static void main(String[] args) {
        PublicInterface i = PublicInterface.createPrivateImplementation();
        i.getVa<caret>x
    }
}""");

    assertEquals(0, myFixture.complete(CompletionType.BASIC, 2).length);
  }

public void test_show_methods_accessible_on_base_type_but_inaccessible_on_cast_type() {
    PsiClass clazz = myFixture.addClass("""

package some;
import another.*;

public class Super {
    void foo() {}
   \s
    void test(Super o) {
      if (o instanceof Sub) {
        o.fo<caret>o
      }
    }
}

""");
    myFixture.addClass("package another; public class Sub extends some.Super {}");

    myFixture.configureFromExistingVirtualFile(clazz.getContainingFile().getVirtualFile());

    myFixture.completeBasic();
    assertEquals(myFixture.getLookupElementStrings(), Arrays.asList("foo"));
  }

private void doTestSecond() {
    configure();
    assertTrue(myItems == null || myItems.length == 0);
    myFixture.completeBasic();
    checkResult();
  }

}
