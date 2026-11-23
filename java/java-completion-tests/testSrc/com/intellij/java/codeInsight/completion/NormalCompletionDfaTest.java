package com.intellij.java.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@IndexingModeTemplate
public class NormalCompletionDfaTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastInstanceofedQualifier() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastInstanceofedQualifierInForeach() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastComplexInstanceofedQualifier() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  public void _testCastIncompleteInstanceofedQualifier() { completion.get().doTest(); }

  @Test
  public void testCastTooComplexInstanceofedQualifier() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testCastInstanceofedThisQualifier() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDontCastInstanceofedQualifier() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testDontCastPartiallyInstanceofedQualifier() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testQualifierCastingWithUnknownAssignments() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testQualifierCastingBeforeLt() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastQualifierForPrivateFieldReference() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOrAssignmentDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAssignmentPreciseTypeDfa() { EdtTestUtil.runInEdtAndWait(() -> doTestSecond()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAssignmentTwicePreciseTypeDfa() { EdtTestUtil.runInEdtAndWait(() -> doTestSecond()); }

  @Test
  public void testAssignmentParameterDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testAssignmentNoPreciseTypeDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAssignmentPrimitiveLiteral() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDeclarationPreciseTypeDfa() { EdtTestUtil.runInEdtAndWait(() -> doTestSecond()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfAssignmentDfa() { EdtTestUtil.runInEdtAndWait(() -> doTestSecond()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testStreamDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full
  public void testStreamIncompleteDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOptionalDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFieldWithCastingCaret() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testCastWhenMethodComesFromDfaSuperType() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGenericTypeDfa() { EdtTestUtil.runInEdtAndWait(() -> doTestSecond()); }

  @Test
  public void testNarrowingReturnType() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testNarrowingReturnTypeInVoidContext() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoUnnecessaryCastDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoUnnecessaryCastRawDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInconsistentHierarchyDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAfterAssertTrueDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testNoUnnecessaryCastDeepHierarchy() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfAfterFunction() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfDisjunction() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfDisjunction2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfDisjunctionDeep() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInstanceOfDisjunctionCircular() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAfterGetClass() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoCastForCompatibleCapture() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testBooleanFlagDfa() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testComplexInstanceOfDfa() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "methodFromX", "methodFromX2", "methodFromY", "methodFromY2");

      assertEquals("() on X", NormalCompletionFixture.renderElement(completion.get().getItems()[0]).getTailText());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastTwice() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "b", "a");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPublicMethodExtendsProtected() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
      completion.get().doTest();
    });
  }

@Test
@NeedsIndex.ForStandardLibrary public void testCastInstanceofedQualifierInLambda() {EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastInstanceofedQualifierInLambda2() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCastInstanceofedQualifierInExpressionLambda() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  public void testCastQualifierInstanceofedTwice() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "boo", "foo", "moo");
      myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
      myFixture.type("\n");
      completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPreferCastExpressionSuperTypes() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package nonImported; public interface SzNameInTheEnd {}");
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "SString", "SzNameInTheEnd", "Serializable");
    });
  }

  @Test
  public void test_no_casts_to_inaccessible_type() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

@Test
public void test_show_methods_accessible_on_base_type_but_inaccessible_on_cast_type() {
  LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
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
    });
  }

private void doTestSecond() {
  LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    completion.get().configure();
    assertTrue(completion.get().getItems() == null || completion.get().getItems().length == 0);
    myFixture.completeBasic();
    completion.get().checkResult();
  }

}
