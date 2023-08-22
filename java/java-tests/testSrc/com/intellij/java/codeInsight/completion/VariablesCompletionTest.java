// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.NeedsIndex;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.util.Set;

public class VariablesCompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath();
  }

  public void testObjectVariable() {
    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java");
    checkResultByFile(FILE_PREFIX + "locals/" + getTestName(false) + "_after.java");
  }

  public void testStringVariable() {
    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java");
    checkResultByFile(FILE_PREFIX + "locals/" + getTestName(false) + "_after.java");
  }

  @NeedsIndex.Full
  public void testInputMethodEventVariable() {
    myFixture.addClass("package java.awt.event; public interface InputMethodEvent {}");

    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java");
    checkResultByFile(FILE_PREFIX + "locals/" + getTestName(false) + "_after.java");
  }

  @NeedsIndex.ForStandardLibrary
  public void testLocals1() {
    doSelectTest("TestSource1.java", "TestResult1.java");
  }

  public void testInterfaceMethod() {
    configureByFile(FILE_PREFIX + "locals/" + "InterfaceMethod.java");
    assertStringItems("calcGooBarDoo", "calcBarDoo", "calcDoo");
  }

  public void testLocals2() {
    configureByFile(FILE_PREFIX + "locals/" + "TestSource2.java");
    myFixture.assertPreferredCompletionItems(0, "abc", "aaa");
    checkResultByFile(FILE_PREFIX + "locals/" + "TestResult2.java");
  }

  public void testLocals3() {
    doTest("TestSource3.java", "TestResult3.java");
  }

  @NeedsIndex.ForStandardLibrary
  public void testLocals4() {
    doSelectTest("TestSource4.java", "TestResult4.java");
  }

  public void testLocals5() {
    doTest("TestSource5.java", "TestResult5.java");
  }

  public void testLocals6() {
    doSelectTest("TestSource6.java", "TestResult6.java");
  }

  public void testLocals7() {
    doTest("TestSource7.java", "TestResult7.java");
  }

  public void testLocalReserved() {
    doTest("LocalReserved.java", "LocalReserved_after.java");
  }

  public void testLocalReserved2() {
    configureByFile(FILE_PREFIX + "locals/" + "LocalReserved2.java");
    checkResultByFile(FILE_PREFIX + "locals/" + "LocalReserved2.java");
    assert !DefaultGroovyMethods.asBoolean(myFixture.getLookupElementStrings());
  }

  public void testUniqueNameInFor() {
    doTest(getTestName(false) + ".java", getTestName(false) + "_after.java");
  }

  public void testWithBuilderParameter() {
    doTest(getTestName(false) + ".java", getTestName(false) + "_after.java");
  }

  private void doTest(String before, String after) {
    configureByFile(FILE_PREFIX + "locals/" + before);
    checkResultByFile(FILE_PREFIX + "locals/" + after);
  }

  private void doSelectTest(String before, String after) {
    configureByFile(FILE_PREFIX + "locals/" + before);
    myFixture.type("\n");
    checkResultByFile(FILE_PREFIX + "locals/" + after);
  }

  public void testLocals8() {
    doTest("TestSource8.java", "TestResult8.java");
  }

  @NeedsIndex.ForStandardLibrary(reason = "With empty indices 'Object' may be considered variable name, and instanceof is ok here; Object is not resolved thus not replaced with 'o'")
  public void testUnresolvedReference() throws Exception {
    configureByFile(FILE_PREFIX + "locals/" + getTestName(false) + ".java");
    assertStringItems("o", "psiClass", "object");
  }

  public void testFieldNameCompletion1() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.FIELD_NAME_PREFIX = "my";
    doSelectTest("FieldNameCompletion1.java", "FieldNameCompletion1-result.java");
  }

  public void testFieldNameCompletion2() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.FIELD_NAME_PREFIX = "my";
    configureByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion2.java");
    checkResultByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion2-result.java");
  }

  public void testFieldNameCompletion3() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.FIELD_NAME_PREFIX = "my";
    configureByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion3.java");
    complete();
    checkResultByFile(FILE_PREFIX + "locals/" + "FieldNameCompletion3-result.java");
  }

  public void testLocals9() {
    doSelectTest("TestSource9.java", "TestResult9.java");
  }

  public void testFieldOutOfAnonymous() {
    doTest("TestFieldOutOfAnonymous.java", "TestFieldOutOfAnonymousResult.java");
  }

  public void testUnresolvedMethodName() {
    configureByFile(FILE_PREFIX + "locals/" + "UnresolvedMethodName.java");
    complete();
    checkResultByFile(FILE_PREFIX + "locals/" + "UnresolvedMethodName_after.java");
  }

  public void testArrayMethodName() {
    doTest("ArrayMethodName.java", "ArrayMethodName-result.java");
  }

  public void test_suggest_fields_that_could_be_initialized_by_super_constructor() {
    myFixture.configureByText(JavaFileType.INSTANCE,
                              "\nclass Foo extends java.util.ArrayList {\n  int field;\n  int field2;\n  Foo() {\n    super();\n    field2 = f<caret>; \n  } \n}");
    complete();
    myFixture.assertPreferredCompletionItems(0, "field", "float");
  }

  public void test_suggest_fields_initialized_by_init() {
    myFixture.configureByText(JavaFileType.INSTANCE,
                              "\nclass Foo {\n  final Integer field;\n  \n  { field = 42; }\n  \n  Foo() {\n    equals(f<caret>); \n  } \n}");
    complete();
    myFixture.assertPreferredCompletionItems(0, "field");
  }

  public void test_suggest_fields_in_their_modification_inside_constructor() {
    myFixture.configureByText(JavaFileType.INSTANCE,
                              "\nclass Foo {\n  int total;\n\n  Foo() {\n    this.total = 0;\n    this.total += this.to<caret>tal\n  }\n}\n");
    complete();
    myFixture.assertPreferredCompletionItems(0, "total");
  }

  @NeedsIndex.ForStandardLibrary(reason = "On empty indices String is not resolved and replaced with name 's', filtered out in JavaMemberNameCompletionContributor.getOverlappedNameVersions for being short")
  public void testInitializerMatters() {
    myFixture.configureByText(JavaFileType.INSTANCE, "class Foo {{ String f<caret>x = getFoo(); }; String getFoo() {}; }");
    complete();
    assertStringItems("foo", "fString");
  }

  public void testFieldInitializerMatters() {
    myFixture.configureByText(JavaFileType.INSTANCE, "class Foo { String f<caret>x = getFoo(); String getFoo() {}; }");
    complete();
    assertStringItems("foo", "fString");
  }

  public void testNoKeywordsInForLoopVariableName() {
    configure();
    assertStringItems("stringBuffer", "buffer");
  }

  public void testDontIterateOverLoopVariable() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "nodes", "new", "null");
  }

  public void testDuplicateSuggestionsFromUsage() {
    configure();
    assertStringItems("preferencePolicy", "policy", "aPreferencePolicy");
  }

  public void testSuggestVariablesInTypePosition() {
    configure();
    assertStringItems("myField", "myField2");
  }

  public void configure() {
    configureByFile(FILE_PREFIX + getTestName(false) + ".java");
  }

  public void testAnnotationValue() {
    configure();
    checkResultByFile(FILE_PREFIX + getTestName(false) + "_after.java");
  }

  @NeedsIndex.ForStandardLibrary(reason = "On empty indices String is not resolved and replaced with name 's', filtered out in JavaMemberNameCompletionContributor.getOverlappedNameVersions for being short")
  public void testConstructorParameterName() {
    configure();
    assertStringItems("color", "coString");
  }

  @NeedsIndex.ForStandardLibrary(reason = "On empty indices String is not resolved and replaced with name 's', filtered out in JavaMemberNameCompletionContributor.getOverlappedNameVersions for being short; P is from PARAMETER_NAME_PREFIX")
  public void testConstructorParameterNameWithPrefix() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.FIELD_NAME_PREFIX = "my";
    settings.PARAMETER_NAME_PREFIX = "p";

    configure();

    assertStringItems("pColor", "pCoPString");
  }

  public void test_finish_with__() {
    myFixture.configureByText("a.java", "\nclass FooFoo {\n  FooFoo f<caret>\n}\n");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "fooFoo", "foo");
    myFixture.type("=");
    myFixture.checkResult("\nclass FooFoo {\n  FooFoo fooFoo = <caret>\n}\n");
  }

  public void test_suggest_variable_names_by_non_getter_initializer_call() {
    myFixture.configureByText("a.java", "\nclass FooFoo {\n  { long <caret>x = System.nanoTime(); }\n}\n");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "l", "nanoTime", "time");
  }

  public void test_use_superclass_for_inner_class_variable_name_suggestion() {
    myFixture.configureByText("a.java",
                              "\nclass FooFoo {\n  { Rectangle2D.Double <caret>x }\n}\nclass Rectangle2D {\n  static class Double extends Rectangle2D {}\n}\n");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "aDouble", "rectangle2D");
  }

  @NeedsIndex.ForStandardLibrary
  public void test_suggest_field_shadowing_parameter_name() {
    myFixture.configureByText("a.java",
                              "\nclass FooFoo {\n  private final Collection<MaterialQuality> materialQualities;\n\n    public Inventory setMaterialQualities(Iterable<MaterialQuality> <caret>) {\n\n    }}\n");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "materialQualities", "materialQualities1", "qualities", "materialQualityIterable",
                                             "qualityIterable", "iterable");
  }

  public void test_suggest_parameter_name_from_javadoc() {
    myFixture.configureByText("a.java",
                              "\nclass FooFoo {\n    /**\n    * @param existing\n    * @param abc\n    * @param def\n    * @param <T>\n    */\n    void set(int existing, int <caret>) {}\n}\n");
    myFixture.completeBasic();
    assertEquals(Set.copyOf(myFixture.getLookupElementStrings()), Set.of("abc", "def", "i"));
  }

  public void test_no_name_suggestions_when_the_type_is_unresolved_because_it_is_actually_a_mistyped_keyword() {
    myFixture.configureByText("a.java", "\nclass C {\n    { \n      retur Fi<caret>x\n    }\n    }\n");
    assertEquals(0, myFixture.completeBasic().length);
  }

  public void test_suggest_variable_name_when_the_type_is_unresolved_but_does_not_seem_a_mistyped_keyword() {
    myFixture.configureByText("a.java", "\nclass C {\n    { \n      UndefinedType <caret>\n    }\n    }\n");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "undefinedType", "type");
  }

  public void test_class_based_suggestions_for_exception_type() {
    myFixture.configureByText("a.java", "\nclass C {\n    { \n      try { } catch (java.io.IOException <caret>)\n    }\n}\n");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "e", "ioException", "exception");
  }

  public void test_no_shadowed_static_field_suggestions() {
    myFixture.configureByText("a.java",
                              "\nclass C extends Super {\n    static final String FOO = \"c\";\n    { \n      C.FO<caret>x\n    }\n}\n\nclass Super {\n  static final String FOO = \"super\";\n}\n");
    LookupElement[] items = myFixture.completeBasic();
    assertStringItems("FOO");
    assertEquals(" ( = \"c\")", NormalCompletionTestCase.renderElement(items[0]).getTailText());
  }

  private static final String FILE_PREFIX = "/codeInsight/completion/variables/";
}
