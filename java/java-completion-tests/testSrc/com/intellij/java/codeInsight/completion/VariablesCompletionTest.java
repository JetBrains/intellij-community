// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightCompletionFixture;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionFixture;
import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@IndexingModeTemplate
public class VariablesCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<LightCompletionFixture> completion = lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testObjectVariable() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(FILE_PREFIX + "locals/" + myFixture.getTestName(false) + ".java");
      completion.get().checkResultByFile(FILE_PREFIX + "locals/" + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testStringVariable() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(FILE_PREFIX + "locals/" + myFixture.getTestName(false) + ".java");
      completion.get().checkResultByFile(FILE_PREFIX + "locals/" + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testInputMethodEventVariable() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package java.awt.event; public interface InputMethodEvent {}");

      completion.get().configureByFile(FILE_PREFIX + "locals/" + myFixture.getTestName(false) + ".java");
      completion.get().checkResultByFile(FILE_PREFIX + "locals/" + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLocals1() {
    doSelectTest("TestSource1.java", "TestResult1.java");
  }

  @Test
  public void testInterfaceMethod() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(FILE_PREFIX + "locals/" + "InterfaceMethod.java");
      completion.get().assertStringItems("calcGooBarDoo", "calcBarDoo", "calcDoo");
    });
  }

  @Test
  public void testLocals2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(FILE_PREFIX + "locals/" + "TestSource2.java");
      myFixture.assertPreferredCompletionItems(0, "abc", "aaa");
      completion.get().checkResultByFile(FILE_PREFIX + "locals/" + "TestResult2.java");
    });
  }

  @Test
  public void testLocals3() {
    doTest("TestSource3.java", "TestResult3.java");
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLocals4() {
    doSelectTest("TestSource4.java", "TestResult4.java");
  }

  @Test
  public void testLocals5() {
    doTest("TestSource5.java", "TestResult5.java");
  }

  @Test
  public void testLocals6() {
    doSelectTest("TestSource6.java", "TestResult6.java");
  }

  @Test
  public void testLocals7() {
    doTest("TestSource7.java", "TestResult7.java");
  }

  @Test
  public void testLocalReserved() {
    doTest("LocalReserved.java", "LocalReserved_after.java");
  }

  @Test
  public void testLocalReserved2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(FILE_PREFIX + "locals/" + "LocalReserved2.java");
      completion.get().checkResultByFile(FILE_PREFIX + "locals/" + "LocalReserved2.java");
      assertTrue(myFixture.getLookupElementStrings().isEmpty());
    });
  }

  @Test
  public void testUniqueNameInFor() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      doTest(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  public void testWithBuilderParameter() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      doTest(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "_after.java");
    });
  }

  private void doTest(String before, String after) {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(FILE_PREFIX + "locals/" + before);
      completion.get().checkResultByFile(FILE_PREFIX + "locals/" + after);
    });
  }

  private void doSelectTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(FILE_PREFIX + "locals/" + before);
      myFixture.type("\n");
      completion.get().checkResultByFile(FILE_PREFIX + "locals/" + after);
    });
  }

  @Test
  public void testLocals8() {
    doTest("TestSource8.java", "TestResult8.java");
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "With empty indices 'Object' may be considered variable name, and instanceof is ok here; Object is not resolved thus not replaced with 'o'")
  public void testUnresolvedReference() throws Exception {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(FILE_PREFIX + "locals/" + myFixture.getTestName(false) + ".java");
      completion.get().assertStringItems("o", "psiClass", "object");
    });
  }

  @Test
  public void testFieldNameCompletion1() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      settings.FIELD_NAME_PREFIX = "my";
      doSelectTest("FieldNameCompletion1.java", "FieldNameCompletion1-result.java");
    });
  }

  @Test
  public void testFieldNameCompletion2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      settings.FIELD_NAME_PREFIX = "my";
      completion.get().configureByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion2.java");
      completion.get().checkResultByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion2-result.java");
    });
  }

  @Test
  public void testFieldNameCompletion3() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      settings.FIELD_NAME_PREFIX = "my";
      completion.get().configureByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion3.java");
      completion.get().complete();
      completion.get().checkResultByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion3-result.java");
    });
  }

  @Test
  public void testLocals9() {
    doSelectTest("TestSource9.java", "TestResult9.java");
  }

  @Test
  public void testFieldOutOfAnonymous() {
    doTest("TestFieldOutOfAnonymous.java", "TestFieldOutOfAnonymousResult.java");
  }

  @Test
  public void testUnresolvedMethodName() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(FILE_PREFIX + "locals/" + "UnresolvedMethodName.java");
      completion.get().complete();
      completion.get().checkResultByFile(FILE_PREFIX + "locals/" + "UnresolvedMethodName_after.java");
    });
  }

  @Test
  public void testArrayMethodName() {
    doTest("ArrayMethodName.java", "ArrayMethodName-result.java");
  }

  @Test
  public void testSuggestFieldsThatCouldBeInitializedBySuperConstructor() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText(JavaFileType.INSTANCE,
                                "\nclass Foo extends java.util.ArrayList {\n  int field;\n  int field2;\n  Foo() {\n    super();\n    field2 = f<caret>; \n  } \n}");
      completion.get().complete();
      myFixture.assertPreferredCompletionItems(0, "field", "float");
    });
  }

  @Test
  public void testSuggestFieldsInitializedByInit() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText(JavaFileType.INSTANCE,
                                "\nclass Foo {\n  final Integer field;\n  \n  { field = 42; }\n  \n  Foo() {\n    equals(f<caret>); \n  } \n}");
      completion.get().complete();
      myFixture.assertPreferredCompletionItems(0, "field");
    });
  }

  @Test
  public void testSuggestFieldsInTheirModificationInsideConstructor() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText(JavaFileType.INSTANCE,
                                "\nclass Foo {\n  int total;\n\n  Foo() {\n    this.total = 0;\n    this.total += this.to<caret>tal\n  }\n}\n");
      completion.get().complete();
      myFixture.assertPreferredCompletionItems(0, "total");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "On empty indices String is not resolved and replaced with name 's', filtered out in JavaMemberNameCompletionContributor.getOverlappedNameVersions for being short")
  public void testInitializerMatters() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText(JavaFileType.INSTANCE, "class Foo {{ String f<caret>x = getFoo(); }; String getFoo() {}; }");
      completion.get().complete();
      completion.get().assertStringItems("foo", "fString");
    });
  }

  @Test
  public void testFieldInitializerMatters() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText(JavaFileType.INSTANCE, "class Foo { String f<caret>x = getFoo(); String getFoo() {}; }");
      completion.get().complete();
      completion.get().assertStringItems("foo", "fString");
    });
  }

  @Test
  public void testNoKeywordsInForLoopVariableName() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("stringBuffer", "buffer");
    });
  }

  @Test
  public void testDontIterateOverLoopVariable() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      myFixture.assertPreferredCompletionItems(0, "nodes", "new", "null");
    });
  }

  @Test
  public void testDuplicateSuggestionsFromUsage() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("preferencePolicy", "policy", "aPreferencePolicy");
    });
  }

  @Test
  public void testSuggestVariablesInTypePosition() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("myField", "myField2");
    });
  }

  public void configure() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile(FILE_PREFIX + myFixture.getTestName(false) + ".java");
    });
  }

  @Test
  public void testAnnotationValue() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().checkResultByFile(FILE_PREFIX + myFixture.getTestName(false) + "_after.java");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "On empty indices String is not resolved and replaced with name 's', filtered out in JavaMemberNameCompletionContributor.getOverlappedNameVersions for being short")
  public void testConstructorParameterName() {
    EdtTestUtil.runInEdtAndWait(() -> {
      configure();
      completion.get().assertStringItems("color", "coString");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary(reason = "On empty indices String is not resolved and replaced with name 's', filtered out in JavaMemberNameCompletionContributor.getOverlappedNameVersions for being short; P is from PARAMETER_NAME_PREFIX")
  public void testConstructorParameterNameWithPrefix() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(myFixture.getProject());
      settings.FIELD_NAME_PREFIX = "my";
      settings.PARAMETER_NAME_PREFIX = "p";

      configure();

      completion.get().assertStringItems("pColor", "pCoPString");
    });
  }

  @Test
  public void testFinishWith_() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "\nclass FooFoo {\n  FooFoo f<caret>\n}\n");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "fooFoo", "foo");
      myFixture.type("=");
      myFixture.checkResult("\nclass FooFoo {\n  FooFoo fooFoo = <caret>\n}\n");
    });
  }

  @Test
  public void testSuggestVariableNamesByNonGetterInitializerCall() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "\nclass FooFoo {\n  { long <caret>x = System.nanoTime(); }\n}\n");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "l", "nanoTime", "time");
    });
  }

  @Test
  public void testUseSuperclassForInnerClassVariableNameSuggestion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java",
                                "\nclass FooFoo {\n  { Rectangle2D.Double <caret>x }\n}\nclass Rectangle2D {\n  static class Double extends Rectangle2D {}\n}\n");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "aDouble", "rectangle2D");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSuggestFieldShadowingParameterName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java",
                                "\nclass FooFoo {\n  private final Collection<MaterialQuality> materialQualities;\n\n    public Inventory setMaterialQualities(Iterable<MaterialQuality> <caret>) {\n\n    }}\n");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "materialQualities", "materialQualities1", "qualities", "materialQualityIterable",
                                               "qualityIterable", "iterable");
    });
  }

  @Test
  public void testSuggestParameterNameFromJavadoc() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java",
                                "\nclass FooFoo {\n    /**\n    * @param existing\n    * @param abc\n    * @param def\n    * @param <T>\n    */\n    void set(int existing, int <caret>) {}\n}\n");
      myFixture.completeBasic();
      assertEquals(Set.copyOf(myFixture.getLookupElementStrings()), Set.of("abc", "def", "i"));
    });
  }

  @Test
  public void testNoNameSuggestionsWhenTheTypeIsUnresolvedBecauseItIsActuallyMistypedKeyword() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "\nclass C {\n    { \n      retur Fi<caret>x\n    }\n    }\n");
      assertEquals(0, myFixture.completeBasic().length);
    });
  }

  @Test
  public void testSuggestVariableNameWhenTheTypeIsUnresolvedButDoesNotSeemMistypedKeyword() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "\nclass C {\n    { \n      UndefinedType <caret>\n    }\n    }\n");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "undefinedType", "type");
    });
  }

  @Test
  public void testClassBasedSuggestionsForExceptionType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "\nclass C {\n    { \n      try { } catch (java.io.IOException <caret>)\n    }\n}\n");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "e", "ioException", "exception");
    });
  }

  @Test
  public void testNoShadowedStaticFieldSuggestions() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java",
                                "\nclass C extends Super {\n    static final String FOO = \"c\";\n    { \n      C.FO<caret>x\n    }\n}\n\nclass Super {\n  static final String FOO = \"super\";\n}\n");
      LookupElement[] items = myFixture.completeBasic();
      completion.get().assertStringItems("FOO");
      assertEquals(" ( = \"c\")", NormalCompletionFixture.renderElement(items[0]).getTailText());
    });
  }

  @Test
  public void testDoubleString() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java",
                                """
                                  public class AA {
                                      public boolean isEnabled(int depth, String t<caret>ag) {
                                      }
                                  }
                                
                                  """);
      LookupElement[] items = myFixture.completeBasic();
      assertEquals(1, Arrays.stream(items).filter(e -> e.getLookupString().equals("string")).count());
    });
  }

  private static final String FILE_PREFIX = "/codeInsight/completion/variables/";
}
