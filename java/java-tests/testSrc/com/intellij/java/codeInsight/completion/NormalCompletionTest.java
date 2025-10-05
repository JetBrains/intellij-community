// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class NormalCompletionTest extends NormalCompletionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testSimple() throws Exception {
    configureByFile("Simple.java");
    assertStringItems("_local1", "_local2", "_field", "_baseField", "_method", "_baseMethod");
  }

  public void testCastToPrimitive1() {
    configureByFile("CastToPrimitive1.java");

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    fail();
  }

  public void testCastToPrimitive2() {
    configureByFile("CastToPrimitive2.java");

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    fail();
  }

  public void testCastToPrimitive3() {
    configureByFile("CastToPrimitive3.java");

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    fail();
  }

  public void testWriteInInvokeLater() {
    configureByFile("WriteInInvokeLater.java");
  }

  public void testQualifiedNew1() {
    configure();
    assertStringItems("IInner", "Inner");
  }

  public void testQualifiedNew2() {
    configure();
    assertStringItems("AnInner", "Inner");
  }

  public void testKeywordsInName() {
    doTest("a\n");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSimpleVariable() { doTest("\n"); }

  public void testTypeParameterItemPresentation() {
    configure();
    LookupElementPresentation presentation = renderElement(myItems[0]);
    assertEquals("Param", presentation.getItemText());
    assertEquals(" type parameter of Foo", presentation.getTailText());
    assertNull(presentation.getTypeText());
    assertNull(presentation.getIcon());
    assertFalse(presentation.isItemTextBold());

    presentation = renderElement(myItems[1]);
    assertEquals("Param2", presentation.getItemText());
    assertEquals(" type parameter of goo", presentation.getTailText());
  }

  public void testDisplayDefaultValueInAnnotationMethods() {
    configure();
    LookupElementPresentation presentation = renderElement(myItems[0]);
    assertEquals("myInt", presentation.getItemText());
    assertEquals(" default 42", presentation.getTailText());
    assertTrue(presentation.getTailFragments().get(0).isGrayed());
    assertNull(presentation.getTypeText());
    assertFalse(presentation.isItemTextBold());

    presentation = renderElement(myItems[1]);
    assertEquals("myString", presentation.getItemText());
    assertEquals(" default \"unknown\"", presentation.getTailText());
  }

  @NeedsIndex.ForStandardLibrary
  public void testMethodItemPresentation() {
    configure();
    LookupElementPresentation presentation = renderElement(myItems[0]);
    assertEquals("equals", presentation.getItemText());
    assertEquals("(Object anObject)", presentation.getTailText());
    assertEquals("boolean", presentation.getTypeText());

    assertFalse(ContainerUtil.exists(presentation.getTailFragments(), LookupElementPresentation.TextFragment::isGrayed));
    assertTrue(presentation.isItemTextBold());
  }

  public void testFieldItemPresentationGenerics() {
    configure();
    LookupElementPresentation presentation = renderElement(myItems[0]);
    assertEquals("target", presentation.getItemText());
    assertNull(presentation.getTailText());
    assertEquals("String", presentation.getTypeText());
  }

  @NeedsIndex.ForStandardLibrary
  public void testMethodItemPresentationGenerics() {
    configure();
    LookupElementPresentation presentation = renderElement(myItems[1]);
    assertEquals("add", presentation.getItemText());
    assertEquals("(int index, String element)", presentation.getTailText());
    assertEquals("void", presentation.getTypeText());

    presentation = renderElement(myItems[0]);
    assertEquals("(String e)", presentation.getTailText());
    assertEquals("boolean", presentation.getTypeText());

    assertFalse(ContainerUtil.exists(presentation.getTailFragments(), LookupElementPresentation.TextFragment::isGrayed));
    assertTrue(presentation.isItemTextBold());
  }

  public void testPreferLongerNamesOption() {
    configureByFile("PreferLongerNamesOption.java");

    assertEquals(3, myItems.length);
    assertEquals("abcdEfghIjk", myItems[0].getLookupString());
    assertEquals("efghIjk", myItems[1].getLookupString());
    assertEquals("ijk", myItems[2].getLookupString());

    LookupManager.getInstance(getProject()).hideActiveLookup();

    JavaCodeStyleSettings.getInstance(getProject()).PREFER_LONGER_NAMES = false;
    configureByFile("PreferLongerNamesOption.java");

    assertEquals(3, myItems.length);
    assertEquals("ijk", myItems[0].getLookupString());
    assertEquals("efghIjk", myItems[1].getLookupString());
    assertEquals("abcdEfghIjk", myItems[2].getLookupString());
  }

  public void testSCR7208() {
    configureByFile("SCR7208.java");
  }

  @NeedsIndex.ForStandardLibrary
  public void testProtectedFromSuper() {
    configureByFile("ProtectedFromSuper.java");
    Arrays.sort(myItems);
    assertTrue("Exception not found", ContainerUtil.indexOf(Arrays.asList(myItems), le -> le.getLookupString().equals("xxx")) > 0);
  }

  @NeedsIndex.ForStandardLibrary
  public void testBeforeInitialization() {
    configureByFile("BeforeInitialization.java");
    assertNotNull(myItems);
    assertTrue(myItems.length > 0);
  }

  @NeedsIndex.ForStandardLibrary
  public void testProtectedFromSuper2() {
    configureByFile("ProtectedFromSuper.java");
    Arrays.sort(myItems);
    assertTrue("Exception not found", ContainerUtil.indexOf(Arrays.asList(myItems), le -> le.getLookupString().equals("xxx")) > 0);
  }

  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInArrayAnnoInitializer() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInArrayAnnoInitializer2() { doTest("\n"); }

  public void testReferenceParameters() {
    configureByFile("ReferenceParameters.java");
    assertNotNull(myItems);
    myFixture.assertPreferredCompletionItems(0, "AAAA", "AAAB");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = true;
      CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.FIRST_LETTER);
      CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(false);
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = true;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testConstructorName1() {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    configure();
    assertTrue(myFixture.getLookupElementStrings().contains("ABCDE"));
  }

  public void testConstructorName2() {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    configure();
    assertTrue(myFixture.getLookupElementStrings().contains("ABCDE"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testObjectsInThrowsBlock() {
    configureByFile("InThrowsCompletion.java");
    assertEquals("C", myFixture.getLookupElementStrings().get(0));
    assertTrue(myFixture.getLookupElementStrings().contains("B"));
  }

  public void testAnnoParameterValue() {
    configure();
    List<String> strings = myFixture.getLookupElementStrings();
    assertTrue(strings.contains("AssertionError"));
    assertFalse(strings.contains("enum"));
    assertFalse(strings.contains("final"));
    assertFalse(strings.contains("equals"));
    assertFalse(strings.contains("new"));
    assertFalse(strings.contains("null"));
    assertFalse(strings.contains("public"));
    assertFalse(strings.contains("super"));
    assertFalse(strings.contains("null"));
  }

  public void testAfterInstanceof() {
    configureByFile("AfterInstanceof.java");
    assertTrue(myFixture.getLookupElementStrings().contains("A"));
  }

  public void testAfterCast1() {
    configureByFile("AfterCast1.java");

    assertNotNull(myItems);
    assertEquals(2, myItems.length);
  }

  public void testAfterCast2() {
    configureByFile("AfterCast2.java");
    checkResultByFile("AfterCast2-result.java");
  }

  public void testMethodCallForTwoLevelSelection() {
    configureByFile("MethodLookup.java");
    assertEquals(2, myItems.length);
  }

  public void testMethodCallBeforeAnotherStatementWithParen() {
    configureByFile("MethodLookup2.java");
    checkResultByFile("MethodLookup2_After.java");
  }

  public void testMethodCallBeforeAnotherStatementWithParen2() {
    getCodeStyleSettings().METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    configureByFile("MethodLookup2.java");
    checkResultByFile("MethodLookup2_After2.java");
  }

  public void testSwitchEnumLabel() {
    configureByFile("SwitchEnumLabel.java");
    //first B is enum Field, second B is class. They have different presentations and handlers
    assertEquals("[A, B, C, null, B, Object]", ContainerUtil.map(myItems, LookupElement::getLookupString).toString());
  }

  public void testSwitchCaseWithEnumConstant() { doTest(); }

  public void testSecondSwitchCaseWithEnumConstant() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testInsideSwitchCaseWithEnumConstant() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "compareTo", "describeConstable", "equals");
  }

  public void testMethodInAnnotation() {
    configureByFile("Annotation.java");
    checkResultByFile("Annotation_after.java");
  }

  public void testMethodInAnnotation2() {
    configureByFile("Annotation2.java");
    checkResultByFile("Annotation2_after.java");
  }

  public void testMethodInAnnotation3() {
    configureByFile("Annotation3.java");
    checkResultByFile("Annotation3_after.java");
  }

  public void testMethodInAnnotation5() {
    configureByFile("Annotation5.java");
    checkResultByFile("Annotation5_after.java");
  }

  public void testMethodInAnnotation7() {
    configureByFile("Annotation7.java");
    selectItem(myItems[0]);
    checkResultByFile("Annotation7_after.java");
  }

  public void testAnnotationAttrBeforeExisting() {
    doTest("\n");
  }

  public void testAnnotationAttrBeforeExistingBool() {
    doTest("\n");
  }

  public void testEnumInAnnotation() {
    configureByFile("Annotation4.java");
    checkResultByFile("Annotation4_after.java");
  }

  public void testEnumInTypeAnnotation() { doTest(); }

  public void testSecondAttribute() {
    configureByFile("Annotation6.java");
    checkResultByFile("Annotation6_after.java");
  }

  public void testIDEADEV6408() {
    configureByFile("IDEADEV6408.java");
    assertFirstStringItems("boolean", "byte");
  }

  public void testMethodWithLeftParTailType() {
    configureByFile("MethodWithLeftParTailType.java");
    type('(');
    checkResultByFile("MethodWithLeftParTailType_after.java");

    configureByFile("MethodWithLeftParTailType2.java");
    type('(');
    checkResultByFile("MethodWithLeftParTailType2_after.java");
  }

  public void testSuperErasure() {
    configureByFile("SuperErasure.java");
    checkResultByFile("SuperErasure_after.java");
  }

  public void testMethodWithLeftParTailTypeNoPairBrace() {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;
    doTest("(");
  }

  public void testMethodWithLeftParTailTypeNoPairBrace2() {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;

    //no tail type should work the normal way
    configureByFile("MethodWithLeftParTailTypeNoPairBrace.java");
    selectItem(myItems[0]);
    checkResultByFile("MethodWithLeftParTailTypeNoPairBrace_after2.java");
  }

  public void testMethodNoPairBrace() {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;
    doTest("\n");
  }

  public void testExcessSpaceInTypeCast() {
    configure();
    selectItem(myItems[0]);
    checkResult();
  }

  public void testFieldType() { doTest(); }

  public void testFieldOfLocalClass() {
    configure();
    assertEquals("field", renderElement(myItems[0]).getItemText());
    type('\t');
    checkResult();
  }

  public void testPackageInAnnoParam() {
    doTest();
  }

  public void testAnonymousTypeParameter() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralInAnnoParam() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoForceBraces() {
    getCodeStyleSettings().IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    doTest("\n");
  }

  public void testExcludeStringBuffer() {
    JavaProjectCodeInsightSettings.setExcludedNames(getProject(), myFixture.getTestRootDisposable(), StringBuffer.class.getName());
    configure();
    assertFalse(myFixture.getLookupElementStrings().contains("StringBuffer"));
  }

  @NeedsIndex.Full
  public void testExcludeInstanceInnerClasses() {
    JavaProjectCodeInsightSettings.setExcludedNames(getProject(), myFixture.getTestRootDisposable(), "foo");
    myFixture.addClass("package foo; public class Outer { public class Inner {} }");
    myFixture.addClass("package bar; public class Inner {}");
    configure();
    assertEquals("bar.Inner", ((JavaPsiClassReferenceElement)myFixture.getLookupElements()[0]).getQualifiedName());
    assertEquals(myFixture.getLookupElementStrings(), List.of("Inner"));
  }

  @NeedsIndex.Full
  public void testExcludedInstanceInnerClassCreation() {
    JavaProjectCodeInsightSettings.setExcludedNames(getProject(), myFixture.getTestRootDisposable(), "foo");
    myFixture.addClass("package foo; public class Outer { public class Inner {} }");
    myFixture.addClass("package bar; public class Inner {}");
    configure();
    assertEquals("foo.Outer.Inner", ((JavaPsiClassReferenceElement)myFixture.getLookupElements()[0]).getQualifiedName());
    assertEquals(myFixture.getLookupElementStrings(), List.of("Inner"));
  }

  @NeedsIndex.Full
  public void testExcludedInstanceInnerClassQualifiedReference() {
    JavaProjectCodeInsightSettings.setExcludedNames(getProject(), myFixture.getTestRootDisposable(), "foo");
    myFixture.addClass("package foo; public class Outer { public class Inner {} }");
    myFixture.addClass("package bar; public class Inner {}");
    configure();
    assertEquals("foo.Outer.Inner", ((JavaPsiClassReferenceElement)myFixture.getLookupElements()[0]).getQualifiedName());
    assertEquals(myFixture.getLookupElementStrings(), List.of("Inner"));
  }

  @NeedsIndex.Full
  public void testStaticMethodOfExcludedClass() {
    JavaProjectCodeInsightSettings.setExcludedNames(getProject(), myFixture.getTestRootDisposable(), "foo");
    myFixture.addClass("package foo; public class Outer { public static void method() {} }");
    configure();
    assertEquals(myFixture.getLookupElementStrings(), List.of("method"));
  }

  @NeedsIndex.Full
  public void testExcludeWildcards() {
    JavaProjectCodeInsightSettings.setExcludedNames(getProject(), myFixture.getTestRootDisposable(), "foo.Outer.*1*");
    myFixture.addClass("""
    package foo;
    public class Outer {
      public static void method1() { }

      public static void method2() { }

      public static void method12() { }

      public static void method42() { }
    }
    """);
    myFixture.configureByText("a.java", "class C {{ foo.Outer.m<caret> }}");
    myFixture.completeBasic();
    assertEquals(myFixture.getLookupElementStrings(), List.of("method2", "method42"));
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for int hashCode lookup preventing autocompletion and additional \n)")
  public void testAtUnderClass() {
    doTest("\n");
  }

  public void testLocalClassName() { doTest(); }

  public void testAssigningFieldForTheFirstTime() { doTest(); }

  public void testClassTypeParameters() {
    configure();
    assertTrue(myFixture.getLookupElementStrings().contains("K"));
  }

  public void testClassTypeParametersGenericBounds() {
    configure();
    assertTrue(myFixture.getLookupElementStrings().contains("K"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testLocalClassTwice() {
    configure();
    assertOrderedEquals(myFixture.getLookupElementStrings(),
                        "Zoooz", "Zooooo", "ZoneRulesException", "ZoneRulesProvider", "ZipOutputStream");
  }

  @NeedsIndex.ForStandardLibrary
  public void testLocalTopLevelConflict() {
    configure();
    assertOrderedEquals(myFixture.getLookupElementStrings(),
                        "Zoooz", "Zooooo", "ZoneRulesException", "ZoneRulesProvider", "ZipOutputStream");
  }

  @NeedsIndex.ForStandardLibrary
  public void testFinalBeforeMethodCall() {
    configure();
    assertStringItems("final", "finalize");
  }

  public void testMethodCallAfterFinally() { doTest(); }

  public void testPrivateInAnonymous() { doTest(); }

  public void testStaticMethodFromOuterClass() {
    configure();
    assertStringItems("foo", "A.foo", "for");
    assertEquals("A.foo", renderElement(myItems[1]).getItemText());
    selectItem(myItems[1]);
    checkResult();
  }

  public void testInstanceMethodFromOuterClass() {
    configure();
    assertStringItems("foo", "A.this.foo", "for");
    assertEquals("A.this.foo", renderElement(myItems[1]).getItemText());
    selectItem(myItems[1]);
    checkResult();
  }

  public void testMethodParenthesesSpaces() {
    getCodeStyleSettings().SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
    getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    doTest();
  }

  public void testMethodParenthesesSpacesArgs() {
    getCodeStyleSettings().SPACE_BEFORE_METHOD_CALL_PARENTHESES = true;
    getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    doTest();
  }

  public void testAtUnderClassNoModifiers() {
    doTest();
  }

  public void testBreakInIfCondition() { doTest(); }

  public void testAccessStaticViaInstance() { doTest(); }

  public void testIfConditionLt() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "getAnnotationsAreaOffset");
  }

  @NeedsIndex.ForStandardLibrary(reason = "On emptly indices 'foo' is the only item, so is not filtered out in  JavaCompletionProcessor.dispreferStaticAfterInstance")
  public void testAccessStaticViaInstanceSecond() {
    configure();
    assertFalse(myFixture.getLookupElementStrings().contains("foo"));
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.assertPreferredCompletionItems(0, "foo");
    myFixture.type('\n');
    checkResult();
  }

  public void testAccessInstanceFromStaticSecond() {
    configure();
    myFixture.complete(CompletionType.BASIC, 2);
    checkResult();
  }

  public void testBreakLabel() {
    myFixture.configureByText("a.java", """
      class a {{
        foo: while (true) break <caret>
      }}""");
    complete();
    assertEquals(myFixture.getLookupElementStrings(), List.of("foo"));
  }

  public void testContinueLabel() {
    myFixture.configureByText("a.java", """
      class a {{
        foo: while (true) continue <caret>
      }}""");
    complete();
    assertEquals(myFixture.getLookupElementStrings(), List.of("foo"));
  }

  public void testContinueLabelTail() {
    myFixture.configureByText("a.java", """
      class a {{
        foo: while (true) con<caret>
      }}""");
    complete();
    myFixture.checkResult("""
                            class a {{
                              foo: while (true) continue <caret>
                            }}""");
  }

  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testAnonymousProcess() {
    myFixture.addClass("package java.lang; public class Process {}");
    myFixture.addClass("""
        import java.util. *;
        public class Process {
        }
        interface Pred<A> {
          boolean predicate(A elem);
        }
        public class ListUtils {
          public static <A> List<A> filter(List<A> list, Pred<A> pred) { }
        }""");
    configure();
    type('\n');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResult();
  }

  public void testNoThisInComment() { doAntiTest(); }

  public void testIncNull() {
    configure();
    checkResultByFile(getTestName(false) + ".java");
    assertFalse(myFixture.getLookupElementStrings().contains("null"));
  }

  public void testLastExpressionInFor() { doTest(); }

  public void testOnlyKeywordsInsideSwitch() {
    configureByFile(getTestName(false) + ".java");
    List<String> strings = myFixture.getLookupElementStrings();
    assertNotNull(strings);
    assertSameElements(strings, "case", "default");
  }

  @NeedsIndex.ForStandardLibrary
  public void testBooleanLiterals() {
    doTest("\n");
  }

  public void testDoubleBooleanInParameter() {
    configure();
    assertFirstStringItems("boolean", "byte");
  }

  public void testDoubleConstant() {
    configure();
    assertStringItems("Intf.XFOO", "XFOO");
  }

  public void testNotOnlyKeywordsInsideSwitch() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testChainedCallOnNextLine() {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResult();
  }

  public void testFinishWithDot() {
    configureByFile(getTestName(false) + ".java");
    type('.');
    checkResult();
  }

  public void testEnclosingThis() { doTest(); }

  @NeedsIndex.Full
  public void testSeamlessConstant() {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResult();
  }

  public void testDefaultAnnoParam() { doTest(); }

  public void testSpaceAfterLookupString() {
    configureByFile(getTestName(false) + ".java");
    type(' ');
    assertNull(getLookup());
    checkResult();
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoSpaceInParensWithoutParams() {
    getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    try {
      doTest();
    }
    finally {
      getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    }
  }

  @NeedsIndex.ForStandardLibrary
  public void testTwoSpacesInParensWithParams() {
    getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testQualifierAsPackage() {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResult();
  }

  @NeedsIndex.ForStandardLibrary
  public void testQualifierAsPackage2() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testQualifierAsPackage3() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreselectEditorSelection() {
    configure();
    assertNotSame(getLookup().getCurrentItem(), myFixture.getLookupElements()[0]);
    assertEquals("finalize", getLookup().getCurrentItem().getLookupString());
  }

  public void testNoMethodsInNonStaticImports() {
    configure();
    assertStringItems("*");
  }

  @NeedsIndex.ForStandardLibrary
  public void testMembersInStaticImports() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testPackageNamedVariableBeforeAssignment() {
    doTest();
  }

  public void testInnerEnumConstant() { doTest("\n"); }

  public void testNoExpectedReturnTypeDuplication() {
    configure();
    assertEquals(myFixture.getLookupElementStrings(), List.of("boolean", "byte"));
  }

  public void testNoExpectedVoidReturnTypeDuplication() {
    configure();
    assertEquals(myFixture.getLookupElementStrings(), List.of("void"));
  }

  public void testNoExpectedArrayTypeDuplication() {
    configure();
    assertEquals(myFixture.getLookupElementStrings(), List.of("char"));
  }

  public void testShadowedTypeParameter() {
    configure();
    assertEquals(myFixture.getLookupElementStrings(), List.of("MyParam"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testMethodReturnType() { doTest("\n"); }

  public void testMethodReturnTypeNoSpace() {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResult();
  }

  public void testEnumWithoutConstants() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testDoWhileMethodCall() {
    doTest();
  }

  public void testSecondTypeParameterExtends() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testGetterWithExistingNonEmptyParameterList() {
    doTest();
  }

  public void testNothingAfterNumericLiteral() { doAntiTest(); }

  public void testNothingAfterTypeParameterQualifier() { doAntiTest(); }

  public void testExcludeVariableBeingDeclared() { doAntiTest(); }

  public void testExcludeVariableBeingDeclared2() { doAntiTest(); }

  public void testSpacesAroundEq() { doTest("="); }

  void _testClassBeforeCast() { doTest("\n"); }

  public void testNoAllClassesOnQualifiedReference() {
    doAntiTest();
  }

  public void testFinishClassNameWithDot() {
    doTest(".");
  }

  @NeedsIndex.ForStandardLibrary
  public void testFinishClassNameWithLParen() {
    doTest("(");
  }

  public void testSelectNoParameterSignature() {
    configureByFile(getTestName(false) + ".java");
    final int parametersCount = ((PsiMethod)getLookup().getCurrentItem().getObject()).getParameterList().getParametersCount();
    assertEquals(0, parametersCount);
    type('\n');
    checkResult();
  }

  @NeedsIndex.ForStandardLibrary
  public void testCompletionInsideClassLiteral() {
    configureByFile(getTestName(false) + ".java");
    type('\n');
    checkResult();
  }

  public void testFieldNegation() { doTest("!"); }

  public void testDefaultInSwitch() { doTest(); }

  public void testBreakInSwitch() { doTest(); }

  public void testSuperInConstructor() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testSuperInConstructorWithParams() {
    doTest();
  }

  public void testSuperInMethod() {
    doTest();
  }

  public void testSecondMethodParameterName() {
    doTest();
  }

  public void testAnnotationAsUsualObject() {
    doTest();
  }

  public void testAnnotationAsUsualObjectFromJavadoc() {
    doTest();
  }

  public void testAnnotationAsUsualObjectInsideClass() {
    doTest();
  }

  public void testAnnotationOnNothingParens() {
    doTest();
  }

  public void testMultiResolveQualifier() {
    doTest();
  }

  public void testSecondMethodParameter() { doTest(); }

  public void testReturnInCase() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testUnboxedConstantsInCase() { doTest(); }

  public void testAnnotationWithoutValueMethod() {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("bar", "foo");
  }

  public void testAddExplicitValueInAnnotation() {
    configureByTestName();
    assertStringItems("bar", "goo");
    selectItem(myItems[0]);
    checkResult();
  }

  public void testUnnecessaryMethodMerging() {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("fofoo", "fofoo");
  }

  @NeedsIndex.ForStandardLibrary
  public void testMethodMergingMinimalTail() { doTest(); }

  public void testAnnotationQualifiedName() {
    doTest();
  }

  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testClassNameAnonymous() {
    doTest("\n");
  }

  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  public void testClassNameWithInner() {
    configure();
    assertStringItems("Zzoo", "Zzoo.Impl");
    type('\n');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResult();
  }

  public void testClassNameWithInner2() { doTest("\n"); }

  public void testClassNameWithInstanceInner() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testDoubleFalse() {
    configureByFile(getTestName(false) + ".java");
    assertFirstStringItems("false", "fefefef", "float", "finalize");
  }

  public void testSameNamedVariableInNestedClasses() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "ffid", "Beda.this.ffid");
    selectItem(myItems[1]);
    checkResult();
  }

  public void testHonorUnderscoreInPrefix() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoSemicolonAfterExistingParenthesesEspeciallyIfItsACast() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testNoSemicolonInForUpdate() { doTest("\n"); }

  public void testReturningTypeVariable() { doTest(); }

  public void testReturningTypeVariable2() { doTest(); }

  public void testReturningTypeVariable3() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testImportInGenericType() {
    configure();
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.type('\n');
    checkResult();
  }

  public void testCaseTailType() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_11, this::doTest);
  }

  private void doPrimitiveTypeTest() {
    configure();
    checkResultByFile(getTestName(false) + ".java");
    assertTrue(myFixture.getLookupElementStrings().contains("boolean"));
  }

  public void testFinalInForLoop() {
    configure();
    assertStringItems("final");
  }

  @NeedsIndex.ForStandardLibrary
  public void testFinalInForLoop2() {
    configure();
    myFixture.assertPreferredCompletionItems(1, "finalize", "final");
  }

  public void testOnlyClassesInExtends() {
    configure();
    assertStringItems("Inner");
  }

  public void testNoThisClassInExtends() {
    configure();
    assertStringItems("Fooxxxx2");
  }

  public void testPrimitiveTypesInForLoop() { doPrimitiveTypeTest(); }

  public void testPrimitiveTypesInForLoop2() { doPrimitiveTypeTest(); }

  public void testPrimitiveTypesInForLoop3() { doPrimitiveTypeTest(); }

  public void testPrimitiveTypesInForLoop4() { doPrimitiveTypeTest(); }

  public void testPrimitiveTypesInForLoop5() { doPrimitiveTypeTest(); }

  public void testPrimitiveTypesInForLoop6() { doPrimitiveTypeTest(); }

  public void testPrimitiveTypesInForLoopSpace() {
    configure();
    myFixture.type(' ');
    checkResultByFile(getTestName(false) + "_after.java");
  }

  @NeedsIndex.SmartMode(reason = "Ordering requires smart mode")
  public void testSuggestInaccessibleOnSecondInvocation() {
    configure();
    assertStringItems("_bar", "_goo");
    complete();
    assertStringItems("_bar", "_goo", "_foo");
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    selectItem(getLookup().getItems().get(2), Lookup.NORMAL_SELECT_CHAR);
    checkResult();
  }

  public void testNoCommonPrefixInsideIdentifier() {
    final String path = getTestName(false) + ".java";
    configureByFile(path);
    checkResultByFile(path);
    assertStringItems("fai1", "fai2");
  }

  @NeedsIndex.Full
  public void testProtectedInaccessibleOnSecondInvocation() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.type('\n');
    checkResult();
  }

  public void testPropertyReferencePrefix() {
    myFixture.addFileToProject("test.properties", "foo.bar=Foo! Bar!").getVirtualFile();
    doAntiTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testSecondAnonymousClassParameter() { doTest(); }

  public void testSpaceAfterReturn() {
    configure();
    type('\n');
    checkResult();
  }

  public void testIntersectionTypeMembers() {
    configure();
    assertStringItems("fooa", "foob");
  }

  public void testNoReturnInTernary() { doTest(); }

  public void testWildcardsInLookup() {
    configure();
    assertNotNull(getLookup());
    type("*fz");
    assertNull(getLookup());
  }

  public void testSmartEnterWrapsConstructorCall() { doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR)); }

  public void testSmartEnterNoNewLine() { doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR)); }

  public void testSmartEnterWithNewLine() { doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR)); }

  @NeedsIndex.SmartMode(reason = "MethodCallFixer.apply needs smart mode to count number of parameters")
  public void testSmartEnterGuessArgumentCount() { doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR)); }

  public void testSmartEnterInsideArrayBrackets() { doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR)); }

  public void testTabReplacesMethodNameWithLocalVariableName() { doTest("\t"); }

  public void testMethodParameterAnnotationClass() { doTest(); }

  @NeedsIndex.Full
  public void testInnerAnnotation() {
    configure();
    assertEquals(myFixture.getLookupElementStrings(), List.of("Dependency"));
    type('\t');
    checkResult();
  }

  public void testPrimitiveCastOverwrite() { doTest(); }

  public void testClassReferenceInFor() { doTest(" "); }

  public void testClassReferenceInFor2() { doTest(" "); }

  public void testClassReferenceInFor3() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    doTest(" ");
  }

  public void testEnumConstantFromEnumMember() { doTest(); }

  public void testPrimitiveMethodParameter() { doTest(); }

  public void testPrimitiveVarargMethodParameter() { doTest("."); }

  public void testPrimitiveVarargMethodParameter2() { doTest("."); }

  public void testNewExpectedClassParens() { doTest("\n"); }

  public void testQualifyInnerMembers() { doTest("\n"); }

  public void testDeepInner() {
    configure();
    assertEquals(myFixture.getLookupElementStrings(), List.of("ClassInner1", "ClassInner1.ClassInner2"));
    selectItem(getLookup().getItems().get(1));
    checkResult();
  }

  public void testSuggestExpectedTypeMembers() { doTest("\n"); }

  public void testSuggestExpectedTypeMembersInCall() { doTest("\n"); }

  public void testSuggestExpectedTypeMembersInAnno() { doTest("\n"); }

  public void testExpectedTypesDotSelectsItem() { doTest("."); }

  @NeedsIndex.Full
  public void testExpectedTypeMembersVersusStaticImports() {
    configure();
    assertStringItems("XFOO", "XFOX");
  }

  @NeedsIndex.Full
  public void testSuggestExpectedTypeMembersNonImported() {
    myFixture.addClass("package foo; public class Super { public static final Super FOO = null; }");
    myFixture.addClass("package foo; public class Usage { public static void foo(Super s) {} }");
    doTest("\n");
  }

  @NeedsIndex.Full
  public void testStaticallyImportedInner() {
    configure();
    assertStringItems("AIOInner", "ArrayIndexOutOfBoundsException");
  }

  public void testClassNameInIfBeforeIdentifier() {
    myFixture.addClass("public class ABCDEFFFFF {}");
    doTest("\n");
  }

  public void testClassNameWithInnersTab() { doTest("\t"); }

  public void testClassNameWithGenericsTab() { doTest("\t"); }

  public void testLiveTemplatePrefixTab() { doTest("\t"); }

  public void testOnlyAnnotationsAfterAt() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyAnnotationsAfterAt2() { doTest("\n"); }

  public void testAnnotationBeforeIdentifier() { doTest("\n"); }

  public void testAnnotationBeforeQualifiedReference() { doTest("\n"); }

  public void testAnnotationBeforeIdentifierFinishWithSpace() { doTest(" "); }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInCatch1() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInCatch2() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInCatch3() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInCatch4() { doTest("\n"); }

  public void testCommaAfterVariable() { doTest(","); }

  public void testClassAngleBracket() { doTest("<"); }

  public void testNoArgsMethodSpace() { doTest(" "); }

  public void testClassSquareBracket() { doTest("["); }

  public void testPrimitiveSquareBracket() { doTest("["); }

  public void testVariableSquareBracket() { doTest("["); }

  public void testMethodSquareBracket() { doTest("["); }

  public void testMethodParameterTypeDot() { doAntiTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testNewGenericClass() { doTest("\n"); }

  public void testNewGenericInterface() { doTest(); }

  public void testEnumPrivateFinal() { doTest(); }

  public void testNoFieldsInImplements() { doTest(); }

  public void testSwitchConstantsFromReferencedClass() { doTest("\n"); }

  public void testSwitchValueFinishWithColon() { doTest(":"); }

  public void testUnfinishedMethodTypeParameter() {
    configure();
    assertStringItems("MyParameter", "MySecondParameter");
  }

  public void testUnfinishedMethodTypeParameter2() {
    configure();
    assertStringItems("MyParameter", "MySecondParameter");
  }

  @NeedsIndex.Full
  public void testSuperProtectedMethod() {
    myFixture.addClass("""
                         package foo;
                         public class Bar {
                             protected void foo() { }
                         }""");
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testOuterSuperMethodCall() {
    configure();
    assertEquals("Class2.super.put", renderElement(myItems[0]).getItemText());
    type('\n');
    checkResult();
  }

  public void testTopLevelClassesFromPackaged() {
    myFixture.addClass("public class Fooooo {}");
    var text = "package foo; class Bar { Fooo<caret> }";
    var file = myFixture.addFileToProject("foo/Bar.java", text);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    assertEmpty(myFixture.completeBasic());
    myFixture.checkResult(text);
  }

  public void testRightShift() {
    configure();
    assertStringItems("myField1", "myField2");
  }

  @NeedsIndex.ForStandardLibrary
  public void testAfterCommonPrefix() {
    configure();
    type("eq");
    assertFirstStringItems("equals", "equalsIgnoreCase");
    complete();
    assertFirstStringItems("equals", "equalsIgnoreCase");
    type('(');
    checkResult();
  }

  @NeedsIndex.ForStandardLibrary
  public void testClassNameInsideIdentifierInIf() {
    configure();
    myFixture.complete(CompletionType.BASIC, 2);
    type('\n');
    checkResult();
  }

  public void testKeywordSmartEnter() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "null", "nullity");
    myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT);
    checkResult();
  }

  public void testSynchronizedArgumentSmartEnter() { doTest(String.valueOf(Lookup.COMPLETE_STATEMENT_SELECT_CHAR)); }

  @NeedsIndex.Full
  public void testImportStringValue() {
    myFixture.addClass("package foo; public class StringValue {}");
    myFixture.addClass("package java.lang; class StringValue {}");
    configure();
    myFixture.complete(CompletionType.BASIC, 2);
    type(' ');
    checkResult();
  }

  public void testPrimitiveArrayWithRBrace() { doTest("["); }

  @NeedsIndex.Full
  public void testSuggestMembersOfStaticallyImportedClasses() {
    myFixture.addClass("""
                         package foo;
                         public class Foo {
                           public static void foo() {}
                           public static void bar() {}
                         }
                         """);
    doTest("\n");
  }

  @NeedsIndex.Full
  public void testSuggestMembersOfStaticallyImportedClassesUnqualifiedOnly() {
    myFixture.addClass("""
                         package foo;
                         public class Foo {
                           public static void foo() {}
                           public static void bar() {}
                         }
                         """);
    configure();
    complete();
    assertOneElement(myFixture.getLookupElements());
    myFixture.type('\t');
    checkResult();
  }

  @NeedsIndex.Full
  public void testSuggestMembersOfStaticallyImportedClassesConflictWithLocalMethod() {
    myFixture.addClass("""
                         package foo;
                         public class Foo {
                           public static void foo() {}
                           public static void bar() {}
                         }
                         """);
    configure();
    myFixture.assertPreferredCompletionItems(0, "bar", "bar");
    assertEquals("Foo.bar", renderElement(myFixture.getLookupElements()[1]).getItemText());
    myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
    myFixture.type('\t');
    checkResult();
  }

  @NeedsIndex.Full
  public void testSuggestMembersOfStaticallyImportedClassesConflictWithLocalField() {
    myFixture.addClass("""
                         package foo;
                         public class Foo {
                           public static int foo = 1;
                           public static int bar = 2;
                         }
                         """);
    configure();
    myFixture.assertPreferredCompletionItems(0, "bar", "Foo.bar");
    myFixture.getLookup().setCurrentItem(myFixture.getLookupElements()[1]);
    myFixture.type('\t');
    checkResult();
  }

  public void testInstanceMagicMethod() { doTest(); }

  public void testNoDotOverwrite() { doTest("."); }

  public void testNoModifierListOverwrite() { doTest("\t"); }

  public void testStaticInnerExtendingOuter() { doTest(); }

  public void testPrimitiveClass() { doTest(); }

  public void testPrimitiveArrayClass() { doTest(); }

  public void testPrimitiveArrayOnlyClass() { doAntiTest(); }

  public void testPrimitiveArrayInAnno() { doTest(); }

  public void testNewClassAngleBracket() { doTest("<"); }

  public void testNewClassAngleBracketExpected() { doTest("<"); }

  public void testNewClassSquareBracket() { doTest("["); }

  public void testMethodColon() { doTest(":"); }

  public void testVariableColon() { doTest(":"); }

  public void testFinishByClosingParenthesis() { doTest(")"); }

  public void testNoMethodsInParameterType() {
    configure();
    assertFirstStringItems("final", "float");
  }

  @NeedsIndex.Full
  public void testNonImportedClassInAnnotation() {
    myFixture.addClass("package foo; public class XInternalTimerServiceController {}");
    myFixture.configureByText("a.java", """
      class XInternalError {}

      @interface Anno { Class value(); }

      @Anno(XInternal<caret>)
      """);
    myFixture.complete(CompletionType.BASIC, 2);
    assertFirstStringItems("XInternalError.class", "XInternalError", "XInternalTimerServiceController.class",
                           "XInternalTimerServiceController");
  }

  @NeedsIndex.Full
  public void testNonImportedAnnotationClass() {
    myFixture.addClass("package foo; public @interface XAnotherAnno {}");
    configure();
    type('X');
    assertFirstStringItems("XAnno", "XAnotherAnno");
  }

  @NeedsIndex.ForStandardLibrary
  public void testMetaAnnotation() {
    myFixture.configureByText("a.java", "@<caret> @interface Anno {}");
    myFixture.complete(CompletionType.BASIC);
    assertTrue(ContainerUtil.exists(myFixture.getLookup().getItems(), it -> it.getLookupString().equals("Retention")));
  }

  public void testAnnotationClassFromWithinAnnotation() { doTest(); }

  @NeedsIndex.Full
  public void testStaticallyImportedFieldsTwice() {
    myFixture.addClass("""
                           class Foo {
                             public static final int aZOO;
                           }
                         """);
    myFixture.configureByText("a.java", """
        import static Foo.*
        class Bar {{
          aZ<caret>a
        }}
      """);
    assertOneElement(myFixture.completeBasic());
  }

  public void testStaticallyImportedFieldsTwiceSwitch() { doTest(); }

  public void testStatementKeywords() {
    myFixture.configureByText("a.java", """
        class Bar {{
          <caret>xxx
        }}
      """);
    myFixture.completeBasic();
    final var strings = myFixture.getLookupElementStrings();
    assertTrue(strings.contains("if"));
    assertTrue(strings.contains("while"));
    assertTrue(strings.contains("do"));
    assertTrue(strings.contains("new"));
    assertTrue(strings.contains("try"));

    int iNew = strings.indexOf("new");
    assertFalse(strings.subList(iNew+1, strings.size()).contains("new"));
  }

  public void testExpressionKeywords() {
    myFixture.configureByText("a.java", """
        class Bar {{
          foo(<caret>xxx)
        }}
      """);
    myFixture.completeBasic();
    final var strings = myFixture.getLookupElementStrings();
    assertTrue(strings.contains("new"));
  }

  public void testImportAsterisk() {
    myFixture.configureByText("a.java", "import java.lang.<caret>");
    myFixture.completeBasic();
    myFixture.type("*;");
    myFixture.checkResult("import java.lang.*;<caret>");
  }

  public void testDontPreselectCaseInsensitivePrefixMatch() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    myFixture.configureByText("a.java", "import java.io.*; class Foo {{ int fileSize; fil<caret>x }}");
    myFixture.completeBasic();
    assertEquals("fileSize", getLookup().getCurrentItem().getLookupString());
    myFixture.type('e');

    assertEquals("File", getLookup().getItems().get(0).getLookupString());
    assertEquals("fileSize", getLookup().getItems().get(1).getLookupString());
    assertSame(getLookup().getCurrentItem(), getLookup().getItems().get(1));
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoGenericsWhenChoosingWithParen() { doTest("Ma("); }

  @NeedsIndex.ForStandardLibrary
  public void testNoClosingWhenChoosingWithParenBeforeIdentifier() { doTest("("); }

  public void testPackageInMemberType() { doTest(); }

  public void testPackageInMemberTypeGeneric() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testConstantInAnno() { doTest("\n"); }

  @NeedsIndex.SmartMode(reason = "Smart mode needed for EncodingReferenceInjector to provide EncodingReference with variants")
  public void testCharsetName() {
    myFixture.addClass("package java.nio.charset; public class Charset { public static Charset forName(String s) {} }");
    configureByTestName();
    assertTrue(myFixture.getLookupElementStrings().contains("UTF-8"));
  }

  @NeedsIndex.Full
  public void testInnerClassInExtendsGenerics() {
    var text = "package bar; class Foo extends List<Inne<caret>> { public static class Inner {} }";
    myFixture.configureFromExistingVirtualFile(myFixture.addClass(text).getContainingFile().getVirtualFile());
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResult(text.replace("Inne<caret>", "Foo.Inner<caret>"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testClassNameDot() { doTest("."); }

  @NeedsIndex.Full
  public void testClassNameDotBeforeCall() {
    myFixture.addClass("package foo; public class FileInputStreamSmth {}");
    myFixture.configureByFile(getTestName(false) + ".java");
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    type('\b');
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    myFixture.complete(CompletionType.BASIC, 2);
    assertNotNull(getLookup());
    type('.');
    checkResult();
  }

  public void testNoReturnAfterDot() {
    configure();
    assertFalse(myFixture.getLookupElementStrings().contains("return"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testDuplicateExpectedTypeInTypeArgumentList() {
    configure();
    var items = ContainerUtil.filter(myFixture.getLookupElements(), it -> it.getLookupString().equals("String"));
    assertEquals(1, items.size());
    assertEquals(" (java.lang)", renderElement(items.get(0)).getTailText());
  }

  @NeedsIndex.Full
  public void testDuplicateInnerClass() {
    configure();
    var items = ContainerUtil.filter(myFixture.getLookupElements(), it -> it.getLookupString().equals("Inner"));
    assertEquals(1, items.size());
  }

  public void testSameSignature() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "s", "s, file", "s, file, a");
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    myFixture.type('\n');
    checkResult();
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoParenthesesAroundCallQualifier() { doTest(); }

  @NeedsIndex.Full
  public void testAllAssertClassesMethods() {
    myFixture.addClass("package foo; public class Assert { public static boolean foo() {} }");
    myFixture.addClass("package bar; public class Assert { public static boolean bar() {} }");
    configure();
    assertEquals(List.of("Assert.bar", "Assert.foo"), myFixture.getLookupElementStrings());
    myFixture.type('\n');
    checkResult();
  }

  @NeedsIndex.Full
  public void testCastVisually() {
    configure();
    var p = renderElement(myFixture.getLookupElements()[0]);
    assertEquals("getValue", p.getItemText());
    assertTrue(p.isItemTextBold());
    assertEquals("Foo", p.getTypeText());
  }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestEmptySet() {
    configure();
    assertEquals("emptySet", myFixture.getLookupElementStrings().get(0));
    type('\n');
    checkResult();
  }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestAllTypeArguments() {
    configure();
    assertEquals("String, List<String>", getLookup().getItems().get(0).getLookupString());
    assertEquals("String, List<String>", renderElement(getLookup().getItems().get(0)).getItemText());
    type('\n');
    checkResult();
  }

  public void testNoFinalInAnonymousConstructor() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testListArrayListCast() { doTest("\n"); }

  public void testInterfaceImplementationNoCast() { doTest(); }

  @NeedsIndex.Full
  public void testStaticallyImportedMethodsBeforeExpression() { doTest(); }

  @NeedsIndex.Full
  public void testInnerChainedReturnType() { doTest(); }

  private CommonCodeStyleSettings getCodeStyleSettings() {
    return CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  public void testCompatibleInterfacesCast() {
    configure();
    assertTrue(myFixture.getLookupElementStrings().containsAll(List.of("foo", "bar")));
  }

  public void testDontAutoInsertMiddleMatch() {
    configure();
    checkResult();
    assertEquals(1, getLookup().getItems().size());
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for method implementations)")
  public void testImplementViaCompletion() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "private", "protected", "public", "public void run");
    var item = getLookup().getItems().get(3);

    var p = renderElement(item);
    assertEquals("public void run", p.getItemText());
    assertEquals("(String t, int myInt) {...}", p.getTailText());
    assertEquals("Foo", p.getTypeText());

    getLookup().setCurrentItem(item);
    myFixture.type('\n');
    checkResult();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for implementing methods)")
  public void testImplementViaCompletionWithGenerics() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "public void methodWithGenerics", "public void methodWithTypeParam");
    assertEquals("(List k) {...}", renderElement(getLookup().getItems().get(0)).getTailText());
    assertEquals("(K k) {...}", renderElement(getLookup().getItems().get(1)).getTailText());
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  public void testImplementViaOverrideCompletion() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "Override", "OverrideOnly", "Override/Implement methods…", "MustBeInvokedByOverriders", "public void run");
    getLookup().setCurrentItem(getLookup().getItems().get(4));
    myFixture.type('\n');
    checkResult();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  public void testSuggestToOverrideMethodsWhenTypingOverrideAnnotation() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "Override", "OverrideOnly", "Override/Implement methods…");
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    myFixture.type('\n');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResult();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  public void testSuggestToOverrideMethodsWhenTypingOverrideAnnotationBeforeMethod() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "Override", "OverrideOnly", "Override/Implement methods…");
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    myFixture.type('\n');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResult();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  public void testSuggestToOverrideMethodsInMulticaretMode() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "Override", "OverrideOnly", "Override/Implement methods…");
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    myFixture.type('\n');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResult();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for implementing methods)")
  public void testStrikeOutDeprecatedSuperMethods() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "void foo1", "void foo2");
    assertFalse(renderElement(getLookup().getItems().get(0)).isStrikeout());
    assertTrue(renderElement(getLookup().getItems().get(1)).isStrikeout());
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for equals() and hashCode())")
  public void testInvokeGenerateEqualsHashCodeOnOverrideCompletion() {
    configure();
    assertEquals(2, myFixture.getLookupElementStrings().size());
    getLookup().setSelectedIndex(1);
    type('\n');
    checkResult();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for 'toString()')")
  public void testInvokeGenerateToStringOnOverrideCompletion() {
    configure();
    assertEquals(2, myFixture.getLookupElementStrings().size());
    getLookup().setSelectedIndex(1);
    type('\n');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResult();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for equals() and hashCode())")
  public void testDontGenerateEqualsHashCodeOnOverrideCompletion() {
    configure();
    type('\n');
    checkResult();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for 'toString()')")
  public void testDontGenerateToStringOnOverrideCompletion() {
    configure();
    type('\n');
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResult();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters and setters)")
  public void testAccessorViaCompletion() {
    configure();

    var getter = ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals("public int getField"));
    var setter = ContainerUtil.find(myFixture.getLookupElements(), it -> it.getLookupString().equals("public void setField"));
    assertNotNull(myFixture.getLookupElementStrings().toString(), getter);
    assertNotNull(myFixture.getLookupElementStrings().toString(), setter);

    var p = renderElement(getter);
    assertEquals(p.getItemText(), getter.getLookupString());
    assertEquals("() {...}", p.getTailText());
    assertTrue(p.getTypeText().isEmpty());

    p = renderElement(setter);
    assertEquals(p.getItemText(), setter.getLookupString());
    assertEquals("(int field) {...}", p.getTailText());
    assertTrue(p.getTypeText().isEmpty());

    getLookup().setCurrentItem(getter);
    myFixture.type('\n');
    checkResult();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters and setters)")
  public void testNoSetterForFinalField() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "public", "public int getFinalField");
    assertFalse(ContainerUtil.exists(myFixture.getLookupElements(), it -> it.getLookupString().equals("public void setFinalField")));
    assertFalse(ContainerUtil.exists(myFixture.getLookupElements(), it -> it.getLookupString().equals("public int getCONSTANT")));
  }

  public void testBraceOnNextLine() {
    getCodeStyleSettings().BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE;
    doTest();
  }

  public void testDoForceBraces() {
    getCodeStyleSettings().DOWHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    doTest("\n");
  }

  public void testMulticaretSingleItemInsertion() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testMulticaretMethodWithParen() {
    doTest();
  }

  @NeedsIndex.ForStandardLibrary
  public void testMulticaretTyping() {
    configure();
    assertNotNull(getLookup());
    type('p');
    assertNotNull(getLookup());
    type('\n');
    checkResult();
  }

  @NeedsIndex.ForStandardLibrary
  public void testMulticaretCompletionFromNonPrimaryCaret() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "arraycopy");
  }

  public void testMulticaretCompletionFromNonPrimaryCaretWithTab() {
    doTest("\t");
  }

  @NeedsIndex.Full
  public void testCompleteLowercaseClassName() {
    myFixture.addClass("package foo; public class myClass {}");
    myFixture.configureByText("a.java", """
      class Foo extends my<caret>
      """);
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.checkResult("""
                            import foo.myClass;
                            
                            class Foo extends myClass
                            """);
  }

  public void testNoClassesWithDollar() {
    myFixture.addClass("package some; public class $WithDollarNonImported {}");
    myFixture.addClass("package imported; public class $WithDollarImported {}");
    doAntiTest();
  }

  public void testClassesWithDollarInTheMiddle() {
    myFixture.addClass("package imported; public class Foo$WithDollarImported {}");
    configureByTestName();
    myFixture.completeBasic();
    assertTrue(myFixture.getLookupElementStrings().contains("Foo$WithDollarImported"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testDontShowStaticInnerClassAfterInstanceQualifier() {
    myFixture.configureByText("a.java", """
      class Foo {
        static class Inner {}
      }
      class Bar {
        void foo(Foo f) {
          f.<caret>
        }
      }
      """);
    myFixture.completeBasic();
    assertFalse(myFixture.getLookupElementStrings().contains("Inner"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testShowStaticMemberAfterInstanceQualifierWhenNothingMatches() {
    myFixture.configureByText("a.java", "class Foo{{ \"\".<caret> }}");
    myFixture.completeBasic();
    assertFalse(myFixture.getLookupElementStrings().contains("valueOf"));
    ((LookupImpl)myFixture.getLookup()).hide();
    myFixture.type("val");
    myFixture.completeBasic();
    assertTrue(myFixture.getLookupElementStrings().contains("valueOf"));
  }

  public void testNoMathTargetMethods() { doAntiTest(); }

  @NeedsIndex.Full
  public void testNoLowercaseClasses() {
    myFixture.addClass("package foo; public class abcdefgXxx {}");
    doAntiTest();
    myFixture.complete(CompletionType.BASIC, 2);
    assertStringItems("abcdefgXxx");
  }

  @NeedsIndex.Full
  public void testProtectedFieldInAnotherPackage() {
    myFixture.addClass("package foo; public class Super { protected String myString; }");
    doTest();
  }

  @NeedsIndex.Full
  public void testUnimportedStaticInnerClass() {
    myFixture.addClass("package foo; public class Super { public static class Inner {} }");
    doTest();
  }

  public void testNoJavaLangPackagesInImport() { doAntiTest(); }

  @NeedsIndex.Full
  public void testNoStaticDuplicatesFromExpectedMemberFactories() {
    configure();
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.assertPreferredCompletionItems(0, "xcreateZoo", "xcreateElephant");
  }

  public void testNoInaccessibleCompiledElements() {
    configure();
    myFixture.complete(CompletionType.BASIC, 2);
    checkResultByFile(getTestName(false) + ".java");
    assertEmpty(myItems);
    assertNull(getLookup());
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters)")
  public void testCodeCleanupDuringCompletionGeneration() {
    myFixture.configureByText("a.java", "class Foo {int i; ge<caret>}");
    myFixture.enableInspections(new UnqualifiedFieldAccessInspection());
    myFixture.complete(CompletionType.BASIC);
    UIUtil.dispatchAllInvocationEvents();
    myFixture.checkResult("""
                            class Foo {int i;
                            
                                public int getI() {
                                    return this.i;
                                }
                            }""");
  }

  public void testIndentingForSwitchCase() { doTest(); }

  public void testShowMostSpecificOverride() {
    configure();
    assertEquals("B", renderElement(myFixture.getLookup().getItems().get(0)).getTypeText());
  }

  @NeedsIndex.ForStandardLibrary
  public void testShowMostSpecificOverrideOnlyFromClass() {
    configure();
    assertEquals("Door", renderElement(myFixture.getLookup().getItems().get(0)).getTypeText());
  }

  public void testNoOverrideWithMiddleMatchedName() {
    configure();
    assertFalse(myFixture.getLookupElementStrings().contains("public void removeTemporaryEditorNode"));
  }

  public void testShowVarInitializers() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "FIELD1", "FIELD2", "FIELD3", "FIELD4");
    var items = myFixture.getLookup().getItems();
    assertEquals(Arrays.asList("( \"x\")", "(\"y\") {...}", null, " ( = 42)"),
                 ContainerUtil.map(items, item -> renderElement(item).getTailText()));
    assertTrue(renderElement(items.get(3)).getTailFragments().get(0).isItalic());
  }

  @NeedsIndex.Full
  public void testShowNonImportedVarInitializers() {
    configure();
    // Format.Field, DateFormat.Field, NumberFormat.Field, etc. classes are suggested first
    myFixture.assertPreferredCompletionItems(5,
                                             "Field", "Field", "Field", "Field", "Field", "FIELD1", "FIELD2", "FIELD3", "FIELD4");
    var fieldItems = myFixture.getLookup().getItems().subList(5, 9);
    assertEquals(Arrays.asList("( \"x\") in E", "(\"y\") {...} in E", null, " ( = 42) in E"),
                 ContainerUtil.map(fieldItems, item -> renderElement(item).getTailText()));
    assertTrue(renderElement(fieldItems.get(3)).getTailFragments().get(0).isItalic());
    assertFalse(renderElement(fieldItems.get(3)).getTailFragments().get(1).isItalic());
  }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestInterfaceArrayWhenObjectIsExpected() {
    configure();
    assertTrue(renderElement(myFixture.getLookup().getItems().get(0)).getTailText().contains("{...}"));
    assertTrue(renderElement(myFixture.getLookup().getItems().get(1)).getTailText().contains("[]"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestInterfaceArrayWhenObjectArrayIsExpected() {
    configure();
    assertTrue(renderElement(myFixture.getLookup().getItems().get(0)).getTailText().contains("{...}"));
    assertTrue(renderElement(myFixture.getLookup().getItems().get(1)).getTailText().contains("[]"));
  }

  public void testDispreferPrimitiveTypesInCallArgs() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    configure();
    myFixture.assertPreferredCompletionItems(0, "dx", "doo", "Doo", "double");
  }

  @NeedsIndex.ForStandardLibrary
  public void testCopyConstructor() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testGetClassType() {
    configure();
    assertEquals("Class<? extends Number>", renderElement(myFixture.getLookupElements()[0]).getTypeText());
  }

  @NeedsIndex.Full
  public void testNonImportedClassAfterNew() {
    var uClass = myFixture.addClass("package foo; public class U {}");
    myFixture.configureByText("a.java", "class X {{ new U<caret>x }}");
    myFixture.completeBasic();
    assertSame(myFixture.getLookupElements()[0].getObject(), uClass);
  }

  public void testSuggestClassNamesForLambdaParameterTypes() { doTest("\n"); }

  public void testOnlyExtendsSuperInWildcard() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);

    configure();
    assertEquals(myFixture.getLookupElementStrings(), List.of("extends", "super"));
    LookupManager.getInstance(getProject()).hideActiveLookup();

    myFixture.type('n');
    assertEmpty(myFixture.completeBasic());
    myFixture.type('\b');
    checkResultByFile(getTestName(false) + ".java");
  }

  @NeedsIndex.Full
  public void testChainInLambdaBinary() {
    getCodeStyleSettings().ALIGN_MULTILINE_BINARY_OPERATION = true;
    myFixture.addClass("package pkg; public class PathUtil { public static String toSystemDependentName() {} }");
    doTest("\n");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPairAngleBracketDisabled() {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;
    doTest("<");
  }

  public void testDuplicateGenericMethodSuggestionWhenInheritingFromRawType() {
    configure();
    assertEquals(myFixture.getLookupElementStrings(), List.of("indexOf"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testDuplicateEnumValueOf() {
    configure();
    assertEquals(ContainerUtil.map(myFixture.getLookupElements(),
                                   item -> renderElement(item).getItemText()), List.of("Bar.valueOf", "Foo.valueOf", "Enum.valueOf"));
  }

  public void testTypeArgumentInCast() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "String");
  }

  public void testNoCallsInPackageStatement() { doAntiTest(); }

  @NeedsIndex.Full
  public void testTypeParameterShadowingClass() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "Tttt", "Tttt");
    assertTrue(myFixture.getLookupElements()[0].getObject() instanceof PsiTypeParameter);
    assertFalse(myFixture.getLookupElements()[1].getObject() instanceof PsiTypeParameter);
    selectItem(myFixture.getLookupElements()[1]);
    checkResult();
  }

  public void testLowercaseDoesNotMatchUnderscore() {
    configure();
    assertEquals(myFixture.getLookupElementStrings(), List.of("web"));
  }

  public void testLocalClassPresentation() {
    var cls = myFixture.addFileToProject("foo/Bar.java", """
      package foo;
      class Bar {{
          class Local {}
          Lo<caret>x
      }}""");
    myFixture.configureFromExistingVirtualFile(cls.getContainingFile().getVirtualFile());
    var item = myFixture.completeBasic()[0];
    assertTrue(renderElement(item).getTailText().contains("local class"));
  }

  public void testNoDuplicateInCast() {
    configure();
    assertNull(myFixture.getLookupElementStrings());
  }

  public void testNoNonAnnotationMethods() { doAntiTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testPreferBigDecimalToJavaUtilInner() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "BigDecimal", "BigDecimalLayoutForm");
  }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInMultiCatch1() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyExceptionsInMultiCatch2() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyResourcesInResourceList1() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyResourcesInResourceList2() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyResourcesInResourceList3() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyResourcesInResourceList4() { doTest("\n"); }

  public void testOnlyResourcesInResourceList5() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testMethodReferenceNoStatic() { doTest("\n"); }

  public void testMethodReferenceCallContext() { doTest("\n"); }

  @NeedsIndex.Full
  public void testDestroyingCompletedClassDeclaration() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testResourceParentInResourceList() {
    configureByTestName();
    assertEquals("MyOuterResource", myFixture.getLookupElementStrings().get(0));
    assertTrue(myFixture.getLookupElementStrings().contains("MyClass"));
    myFixture.type("C\n");
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void testAfterTryWithResources() {
    configureByTestName();
    var strings = myFixture.getLookupElementStrings();
    assertTrue(strings.containsAll(List.of("final", "finally", "int", "Util")));
  }

  public void testNewObjectHashMapWithSmartEnter() {
    configureByTestName();
    myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  @NeedsIndex.SmartMode(reason = "MethodCallFixer and MissingCommaFixer need resolve in smart mode")
  public void testCompletionShouldNotAddExtraComma() {
    configureByTestName();
    myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT);
    checkResultByFile(getTestName(false) + "_after.java");
  }

  @NeedsIndex.Full
  public void testCompletingClassWithSameNameAsPackage() {
    myFixture.addClass("package Apple; public class Apple {}");
    doTest("\n");
  }

  public void testSuggestGetInstanceMethodName() { doTest(); }

  @NeedsIndex.Full(reason = "AllClassesSearchExecutor.processClassNames from JavaNoVariantsDelegator.suggestNonImportedClasses uses stub indices to provide completion, so matching Scratch class is ignored, ant so is its inner class")
  public void testTabOnNewInnerClass() {
    configureByTestName();
    getLookup().setCurrentItem(ContainerUtil.find(myFixture.getLookupElements(), item -> item.getLookupString().contains("Inner")));
    myFixture.type('\t');
    checkResult();
  }

  @NeedsIndex.Full
  public void testRemoveUnusedImportOfSameName() {
    myFixture.addClass("package foo; public class List {}");
    configureByTestName();
    getLookup().setCurrentItem(ContainerUtil.find(
      myFixture.getLookupElements(), item -> item.getObject() instanceof PsiClass cls && "java.util.List".equals(cls.getQualifiedName())));
    myFixture.type('\n');
    checkResult();
  }

  public void testNoDuplicationAfterNewWithExpectedTypeParameter() {
    myFixture.configureByText("a.java", "class Foo<T> { T t = new <caret> }");
    complete();
    assertTrue(ContainerUtil.filter(myFixture.getLookupElements(), it -> it.getAllLookupStrings().contains("T")).size() < 2);
  }

  public void testNoDuplicationForInnerClassOnSecondInvocation() {
    myFixture.configureByText("a.java", """
      class Abc {
          class FooBar {}
          void foo() {
              FooBar<caret>x
          }
      }""");
    myFixture.complete(CompletionType.BASIC, 2);
    assertEquals(1, myFixture.getLookupElements().length);
  }

  public void testSmartEnterWrapsTypeArguments() {
    myFixture.configureByText("a.java", "class Foo<T> { F<caret>List<String> }");
    myFixture.completeBasic();
    myFixture.type(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
    myFixture.checkResult("class Foo<T> { Foo<List<String>><caret> }");
  }

  public void testNoSuggestionsAfterEnumConstant() { doAntiTest(); }

  public void testPutCaretInsideParensInFixedPlusVarargOverloads() { doTest("\n"); }

  public void testSuggestCurrentClassInSecondSuperGenericParameter() { doTest("\n"); }

  @NeedsIndex.Full
  public void testAfterNewEditingPrefixBackAndForthWhenSometimesThereAreExpectedTypeSuggestionsAndSometimesNot() {
    myFixture.addClass("class Super {}");
    myFixture.addClass("class Sub extends Super {}");
    myFixture.addClass("package foo; public class SubOther {}");
    myFixture.configureByText("a.java", "class C { Super s = new SubO<caret>x }");

    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "SubOther");
    myFixture.type('\b');
    myFixture.assertPreferredCompletionItems(0, "Sub");
    myFixture.type('O');
    myFixture.assertPreferredCompletionItems(0, "SubOther");
  }

  public void testCorrectTypos() {
    myFixture.configureByText("a.java", "class MyClass { MyCals<caret> }");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResult("class MyClass { MyClass<caret> }");
  }

  public void testRemoveParenthesesWhenReplacingEmptyCallWithConstant() {
    doTest("\t");
  }

  public void testNoCallsAfterAnnotationInCodeBlock() { doTest(); }

  public void testExtendsAfterEnum() {
    myFixture.configureByText("a.java", "enum X ex<caret>"); // should not complete
    myFixture.completeBasic();
    myFixture.checkResult("enum X ex");
  }

  @NeedsIndex.Full
  public void testAddImportWhenCompletingInnerAfterNew() {
    myFixture.addClass("package p; public class Outer { public static class Inner {} }");
    configureByTestName();
    selectItem(ContainerUtil.find(myItems, it -> it.getLookupString().contains("Inner")));
    checkResult();
  }

  public void testCompletingQualifiedClassName() {
    myFixture.configureByText("a.java", "class C implements java.util.Li<caret>");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "List");
    myFixture.type('\n');
    myFixture.checkResult("class C implements java.util.List<caret>");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestObjectMethodsWhenSuperIsUnresolved() {
    Consumer<String> checkGetClassPresent = text -> {
      myFixture.configureByText("a.java", text);
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "getClass");
    };
    checkGetClassPresent.accept("class C extends Unresolved {{ getCl<caret>x }}");
    checkGetClassPresent.accept("class C implements Unresolved {{ getCl<caret>x }}");
    checkGetClassPresent.accept("class C extends Unresolved implements Runnable {{ getCl<caret>x }}");
    checkGetClassPresent.accept("class C extends Unresolved1 implements Unresolved2 {{ getCl<caret>x }}");
  }

  public void testSuggestInverseOfDefaultAnnoParamValueForBoolean() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "smth = true", "value = false");

    var smthDefault = ContainerUtil.find(myItems, it -> it.getLookupString().equals("smth = false"));
    var presentation = renderElement(smthDefault);
    assertEquals(" (default)", presentation.getTailText());
    assertTrue(presentation.getTailFragments().get(0).isGrayed());

    myFixture.type('\n');
    checkResult();
  }

  public void testCaseColonAfterStringConstant() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_11, this::doTest);
  }

  public void testOneElementArray() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "aaa", "aaa[0]");
    selectItem(myItems[1]);
    checkResult();
  }

  public void testSuggestChainsOfExpectedType() {
    configure();
    myFixture.assertPreferredCompletionItems(0, "bar", "bar().getGoo");
    selectItem(myItems[1]);
    checkResult();
  }

  public void testTopLevelPublicClass() { doTest(); }

  public void testTopLevelPublicClassIdentifierExists() { doTest(); }

  public void testTopLevelPublicClassBraceExists() { doTest(); }

  public void testNoExceptionsWhenCompletingInapplicableClassNameAfterNew() { doTest("\n"); }

  @NeedsIndex.ForStandardLibrary
  public void testTypeCastCompletion() {
    myFixture.configureByText("a.java", "class X { StringBuilder[] s = (Stri<caret>)}");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResult("class X { StringBuilder[] s = (StringBuilder[]) <caret>}");
  }

  @NeedsIndex.ForStandardLibrary
  public void testTypeCastCompletionNoClosingParenthesis() {
    myFixture.configureByText("a.java", "class X { StringBuilder[] s = (Stri<caret>}");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResult("class X { StringBuilder[] s = (StringBuilder[]) <caret>}");
  }

  @NeedsIndex.ForStandardLibrary
  public void testTypeCastCompletionGeneric() {
    myFixture.configureByText("a.java", "import java.util.*; class X { Map<String, Object> getMap() { return (Ma<caret>)}}");
    var elements = myFixture.completeBasic();
    assertTrue(elements.length > 0);
    LookupElementPresentation presentation = renderElement(elements[0]);
    assertEquals("(Map<String, Object>)", presentation.getItemText());
    myFixture.type('\n');
    myFixture.checkResult("import java.util.*; class X { Map<String, Object> getMap() { return (Map<String, Object>) <caret>}}");
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoExtraSpaceAfterModifier() {
    myFixture.configureByText("a.java", "import java.util.*; class X { prot<caret> @NotNull String foo() {}}");
    myFixture.completeBasic();
    myFixture.checkResult("import java.util.*; class X { protected<caret> @NotNull String foo() {}}");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSuggestUTF8Charset() {
    myFixture.configureByText("a.java", "import java.nio.charset.Charset; class X { Charset test() {return U<caret>;}}");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "StandardCharsets.UTF_8", "StandardCharsets.US_ASCII",
                                             "StandardCharsets.UTF_16", "StandardCharsets.UTF_16BE", "StandardCharsets.UTF_16LE");
    myFixture.type('\n');
    myFixture.checkResult("""
                            import java.nio.charset.Charset;
                            import java.nio.charset.StandardCharsets;

                            class X { Charset test() {return StandardCharsets.UTF_8;}}""");
  }

  @NeedsIndex.ForStandardLibrary
  public void testStaticFieldsInEnumInitializer() {
    myFixture.configureByText("a.java", """
      enum MyEnum {
          A, B, C, D, E, F;
          static int myEnumField;
          static int myEnumField2;
          static final int myEnumField3 = 10;
          static final String myEnumField4 = "";
          {
              System.out.println(myE<caret>);
          }
      }""");
    myFixture.completeBasic();
    assertEquals(myFixture.getLookupElementStrings(), List.of("myEnumField3", "myEnumField4"));
  }

  public void testQualifiedOuterClassName() {
    myFixture.configureByText("a.java", """
      class A {
          private static final long sss = 0L;
          static class B {
              private static final long sss = 0L;
              {
                  <caret>int i = 0;
              }
          }
      }
      """);
    myFixture.completeBasic();
    assertEquals(myFixture.getLookupElementStrings().stream().filter(it -> it.contains("sss")).toList(), List.of("A.sss", "sss"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testPrivateConstructor() {
    myFixture.configureByText("A.java", "class A {{new Syst<caret>}}");
    myFixture.completeBasic();
    assertEquals(ContainerUtil.filter(myFixture.getLookupElementStrings(), it -> it.startsWith("S")),
                 List.of("System.Logger", "System.LoggerFinder"));
  }

  @NeedsIndex.SmartMode(reason = "looks like augments don't work in dumb mode")
  public void testMemberAsJavaKeyword() {
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), PsiAugmentProvider.EP_NAME, new PsiAugmentProvider() {
      @Override
      protected <Psi extends PsiElement> @NotNull List<Psi> getAugments(@NotNull PsiElement element,
                                                                        @NotNull Class<Psi> type,
                                                                        @Nullable String nameHint) {
        if (element instanceof PsiExtensibleClass eClass && eClass.getName().equals("A") && type == PsiMethod.class) {
          PsiMethod method1 = new LightMethodBuilder(getPsiManager(), "default").setMethodReturnType(PsiTypes.voidType());
          PsiMethod method2 = new LightMethodBuilder(getPsiManager(), "define").setMethodReturnType(PsiTypes.voidType());
          return List.of(type.cast(method1), type.cast(method2));
        }
        return Collections.emptyList();
      }
    }, getTestRootDisposable());
    myFixture.configureByText("A.java", "class A {\n  public void test() {\n    Runnable r = A::def<caret>\n  }\n}");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            class A {
                              public void test() {
                                Runnable r = A::define;
                              }
                            }""");
  }

  @NeedsIndex.SmartMode(reason = "isEffectivelyDeprecated needs smart mode")
  public void testNoFinalLibraryClassesInExtends() {
    myFixture.configureByText("X.java", "class StriFoo{}final class StriBar{}class X extends Stri<caret>");
    myFixture.completeBasic();
    List<String> expected = List.of(
      "StriFoo", // non-final project class
      "StringIndexOutOfBoundsException", "StringTokenizer", "StringConcatException", "StringReader", "StringWriter",
      // non-final library classes
      "StringBufferInputStream", // deprecated library class
      "StriBar", // final project class (red)
      "StringTemplate"); // interface (red)
    assertEquals(expected, myFixture.getLookupElementStrings());
  }

  @NeedsIndex.ForStandardLibrary
  public void testPrimitiveTypeAfterAnnotation() {
    myFixture.configureByText("X.java", """
      class C {
        void m() {
          @SuppressWarnings("x") boo<caret>
        }
      }""");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            class C {
                              void m() {
                                @SuppressWarnings("x") boolean
                              }
                            }""");
  }

  @NeedsIndex.SmartMode(reason = "CatchLookupElement works only with smart mode")
  public void testAfterTry() {
    myFixture.configureByText("Test.java", "class X{X() {try {}<caret>}}");
    myFixture.completeBasic();
    assertEquals(myFixture.getLookupElementStrings(),
                 List.of("catch", "finally",
                         "catch (Exception e) {\n    throw new RuntimeException(e);\n}",
                         "catch (RuntimeException e) {\n    throw new RuntimeException(e);\n}"));
  }

  @NeedsIndex.ForStandardLibrary
  public void testInsertNullable() {
    myFixture.configureByText("Test.java", "class X {Stri<caret>}");
    myFixture.completeBasic();
    myFixture.type('?');
    myFixture.checkResult("""
                            import org.jetbrains.annotations.Nullable;

                            class X {
                                @Nullable String
                            }""");
  }

  @NeedsIndex.ForStandardLibrary
  public void testInsertNotNull() {
    myFixture.configureByText("Test.java", "class X {Stri<caret>}");
    myFixture.completeBasic();
    myFixture.type('!');
    myFixture.checkResult("""
                            import org.jetbrains.annotations.NotNull;

                            class X {
                                @NotNull String
                            }""");
  }

  @NeedsIndex.Full
  public void testSuperClassFieldShadowsParameter() {
    myFixture.configureByText("Test.java", """
      class Test {
        static class X {
          int variable;
        }
       \s
        public void test(long variable) {
          new X() {
            double myDouble = vari<caret>
          };
        }
      }""");
    var lookupElements = myFixture.completeBasic();
    assertNull(lookupElements);
    myFixture.checkResult("""
                            class Test {
                              static class X {
                                int variable;
                              }
                             \s
                              public void test(long variable) {
                                new X() {
                                  double myDouble = variable
                                };
                              }
                            }""");
  }

  @NeedsIndex.Full
  public void testVariableNameByTypeName() {
    myFixture.configureByText("Test.java", "class DemoEntity {} class Test {DemoEntity <caret>}");
    myFixture.completeBasic();
    assertEquals(myFixture.getLookupElementStrings(), List.of("demoEntity", "demo", "entity"));
  }

  @NeedsIndex.Full
  public void testCompleteByEqualsAssignment() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    myFixture.configureByText("Test.java", """
      public class Test {
        public static void main(final String[] args) {
          Test test = new Test();
          Test test2 = new Test();
          tes<caret>
        }
      }""");
    myFixture.completeBasic();
    myFixture.type('=');
    myFixture.checkResult("""
                            public class Test {
                              public static void main(final String[] args) {
                                Test test = new Test();
                                Test test2 = new Test();
                                test = <caret>
                              }
                            }""");
  }

  @NeedsIndex.Full
  public void testCompleteByEqualsDeclaration() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    myFixture.configureByText("Test.java", """
      public class Test {
        public static void main(final String[] args) {
          String str<caret>
        }
      }""");
    myFixture.completeBasic();
    myFixture.type('=');
    myFixture.checkResult("""
                            public class Test {
                              public static void main(final String[] args) {
                                String string = <caret>
                              }
                            }""");
  }

  @NeedsIndex.Full
  public void testCompleteByEquals() {
    CodeInsightSettings.getInstance().setSelectAutopopupSuggestionsByChars(true);
    myFixture.configureByText("Test.java", """
      public class Test {
        public static void main(final String[] args) {
          final Test test = new Test();
          if (test.get<caret>)
        }

        public String getFoo() {
          return "";
        }
      }""");
    myFixture.completeBasic();
    myFixture.type('=');
    myFixture.checkResult("""
                            public class Test {
                              public static void main(final String[] args) {
                                final Test test = new Test();
                                if (test.getFoo()=)
                              }

                              public String getFoo() {
                                return "";
                              }
                            }""");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSystemOutNoInitializer() {
    myFixture.configureByText("System.java", """
      package java.lang;
      public class System {
      public static final PrintStream out = null;
      public static void setOut(PrintStream out) {}
      public void test() {System.o<caret>}
      }""");
    var elements = myFixture.completeBasic();
    assertTrue(elements.length > 0);
    var element = elements[0];
    assertEquals("out", element.getLookupString());
    LookupElementPresentation presentation = renderElement(element);
    assertNull(presentation.getTailText());
  }

  @NeedsIndex.ForStandardLibrary(reason = "Control flow analysis needs standard library and necessary to provide variable initializer")
  public void testLocalFromTryBlock() {
    myFixture.configureByText("Test.java", """
      class Test {
        public void test() {
          try {
            int myvar = foo();
          }
          catch (RuntimeException ex) {
          }
          my<caret>
        }

        native int foo();
      }""");
    myFixture.completeBasic();
    myFixture.checkResult("""
                            class Test {
                              public void test() {
                                  int myvar = 0;
                                  try {
                                      myvar = foo();
                                  } catch (RuntimeException ex) {
                                  }
                                  myvar
                              }

                              native int foo();
                            }""");
  }

  public void testLocalFromAnotherIfBranch() {
    myFixture.configureByText("Test.java", """
      class Test {
        public void test(int x) {
          if (x > 0) {
            String result = "positive";
          } else if (x < 0) {
            re<caret>
          }
        }
      }""");
    myFixture.completeBasic();
    assertEquals("return", myFixture.getLookupElements()[0].getLookupString());
    assertEquals("record", myFixture.getLookupElements()[1].getLookupString());
    var element = myFixture.getLookupElements()[2];
    assertEquals("result", element.getLookupString());
    LookupElementPresentation presentation = renderElement(element);
    assertEquals(" (from if-then block)", presentation.getTailText());
    selectItem(element);
    myFixture.checkResult("""
                            class Test {
                              public void test(int x) {
                                  String result = null;
                                  if (x > 0) {
                                      result = "positive";
                                  } else if (x < 0) {
                                      result
                                  }
                              }
                            }""");
  }

  public void testVariableIntoScopeInAnnotation() {
    String source = """
      public class Demo {
          public static void main(String[] args) {
              @SuppressWarnings(<caret>)
              final String code = "println('Hello world')";
          }
      }""";
    myFixture.configureByText("Test.java", source);
    myFixture.complete(CompletionType.SMART);
    assertTrue(myFixture.getLookupElementStrings().isEmpty());
    myFixture.checkResult(source);
  }

  public void testLookupUpDownActions() {
    myFixture.configureByText("Test.java", "class Test {<caret>}");
    myFixture.completeBasic(); // 'abstract' selected
    myFixture.assertPreferredCompletionItems(0, "abstract", "boolean", "byte", "char", "class");
    myFixture.performEditorAction("EditorLookupSelectionDown"); // 'boolean' selected
    myFixture.performEditorAction("EditorLookupSelectionDown"); // 'byte' selected
    myFixture.performEditorAction("EditorLookupSelectionUp"); // 'boolean' selected
    myFixture.type('\n');
    myFixture.checkResult("class Test {boolean}");
  }

  @SuppressWarnings("UnnecessaryUnicodeEscape")
  public void testPinyinMatcher() {
    myFixture.configureByText("Test.java",
                              "class Test {int get\u4F60\u597D() {return 0;} public void test() {int \u4F60\u597D = 1;nh<caret>}}");
    myFixture.completeBasic();
    assertEquals(myFixture.getLookupElementStrings(), List.of("\u4F60\u597D", "get\u4F60\u597D"));
    myFixture.type('\n');
    myFixture.checkResult("class Test {int get\u4F60\u597D() {return 0;} public void test() {int \u4F60\u597D = 1;\u4F60\u597D}}");
  }

  @SuppressWarnings("UnnecessaryUnicodeEscape")
  public void testPinyinMatcher2() {
    myFixture.configureByText("Test.java", "class Test {static public void test() {int \u89D2\u8272 = 3;gj<caret>}}");
    myFixture.completeBasic();
    assertTrue(myFixture.getLookupElementStrings().isEmpty());
    myFixture.type('\b');
    myFixture.completeBasic();
    myFixture.checkResult("class Test {static public void test() {int \u89D2\u8272 = 3;\u89D2\u8272}}");
  }

  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralCompletion() {
    myFixture.configureByText("Test.java", "class Test {Class<? extends CharSequence> get() {return String<caret>}}");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "String", "String.class", "StringBuffer.class", "StringBuffer", "StringBuilder.class",
                                             "StringBuilder",
                                             "StringIndexOutOfBoundsException");
  }

  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralCompletionClassExists() {
    myFixture.configureByText("Test.java", "class Test {Class<? extends CharSequence> get() {return StringBu<caret>.class}}");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResult("class Test {Class<? extends CharSequence> get() {return StringBuffer.class}}");
  }

  @NeedsIndex.ForStandardLibrary
  public void testClassLiteralCompletionNoBound() {
    myFixture.configureByText("Test.java", "class Test {Class<?> get() {return String<caret>}}");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "String", "String.class", "StringBuffer.class", "StringBuffer", "StringBuilder.class",
                                             "StringBuilder",
                                             "StringIndexOutOfBoundsException.class",
                                             "StringIndexOutOfBoundsException");
  }

  public void testCompleteMethodWithoutParentheses() {
    var settings = EditorSettingsExternalizable.getInstance();
    settings.setInsertParenthesesAutomatically(false);
    try {
      myFixture.configureByText("Test.java", "class Test {void myMethod() { myMet<caret> }}");
      myFixture.completeBasic();
      myFixture.checkResult("class Test {void myMethod() { myMethod<caret> }}");
    }
    finally {
      settings.setInsertParenthesesAutomatically(true);
    }
  }

  public void testNoPrimitiveTypeInElseIfCondition() {
    myFixture.configureByText("Test.java", "class X {void a(Object obj) {if(obj instanceof String) {} else if (obj in<caret>)");
    myFixture.completeBasic();
    assertNull(myFixture.getLookupElementStrings());
    myFixture.checkResult("class X {void a(Object obj) {if(obj instanceof String) {} else if (obj instanceof )");
  }

  @NeedsIndex.Full
  public void testNoSelfReferenceInEnum() {
    configure();
    LookupElement[] elements = myFixture.completeBasic();
    LookupElementPresentation presentation = new LookupElementPresentation();
    elements[0].renderElement(presentation);
    assertEquals("(\"1\", AN_IMESSAGE_1) (SomeClass)", presentation.getTailText());
  }

  @NeedsIndex.Full
  public void testPackagePrivateConstantInInterface() {
    myFixture.addClass("""
                         package com.example.x;
                         
                         public interface Exposed extends PackPrivate {
                             int value();
                         }
                         """);
    myFixture.addClass("""
                         package com.example.x;
                         
                         interface PackPrivate {
                             Exposed CONSTANT = () -> 5;
                         }
                         """);
    myFixture.configureByText("Main.java", """
      package com.example;
      
      import com.example.x.Exposed;
      
      public class Main {
          public static void main(String[] args) {
              test(CONST<caret>);
          }
      
          private static void test(Exposed exposed) {
          }
      }""");
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResult("""
      package com.example;
      
      import com.example.x.Exposed;
      
      public class Main {
          public static void main(String[] args) {
              test(Exposed.CONSTANT);
          }
      
          private static void test(Exposed exposed) {
          }
      }""");
  }

  @NeedsIndex.ForStandardLibrary
  public void testDoubleNMatching() {
    myFixture.configureByText("Test.java", """
                         class X {
                           @NN<caret>
                           void test() {}
                         }
                         """);
    myFixture.completeBasic();
    assertEquals(List.of("NonNls", "NotNull", "NotNullByDefault", "UnknownNullability"), myFixture.getLookupElementStrings());
  }

  @NeedsIndex.Full
  public void testEnumMapNoTypeParams() {
    myFixture.configureByText("Test.java", """
      import java.util.Map;
      
      public abstract class SuperClass {
          enum X {A, B, C}
      
          void run() {
              Map<X, String> map = new EnumM<caret>
          }
      }
      """);
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResult("""
                            import java.util.EnumMap;
                            import java.util.Map;
                            
                            public abstract class SuperClass {
                                enum X {A, B, C}
                            
                                void run() {
                                    Map<X, String> map = new EnumMap<>()
                                }
                            }
                            """);
  }
  @NeedsIndex.Full
  public void testTagAdd() {
    Registry.get("java.completion.methods.use.tags").setValue(true, getTestRootDisposable());
    myFixture.configureByText("Test.java", """
      import java.util.HashSet;
      
      public abstract class SuperClass {
      
          void run() {
              HashSet<Object> objects = new HashSet<>();
              objects.len<caret>;
          }
      }
      """);
    myFixture.completeBasic();
    myFixture.type('\n');
    myFixture.checkResult("""
                            import java.util.HashSet;
                            
                            public abstract class SuperClass {
                            
                                void run() {
                                    HashSet<Object> objects = new HashSet<>();
                                    objects.size();
                                }
                            }
                            """);
  }

  @NeedsIndex.Full
  public void testTagAddWithoutStatic() {
    Registry.get("java.completion.methods.use.tags").setValue(true, getTestRootDisposable());
    myFixture.configureByText("Test.java", """
      import java.util.HashSet;
      
      public abstract class SuperClass {
      
          void run() {
              HashSet<Object> objects = new HashSet<>();
              objects.len<caret>;
          }
      
          static void len(HashSet<Object> o){}
      }
      """);
    myFixture.complete(CompletionType.BASIC, 2);
    myFixture.type('\n');
    myFixture.checkResult("""
                            import java.util.HashSet;
                            
                            public abstract class SuperClass {
                            
                                void run() {
                                    HashSet<Object> objects = new HashSet<>();
                                    objects.size();
                                }
                            
                                static void len(HashSet<Object> o){}
                            }
                            """);
  }

  @NeedsIndex.Full
  public void testTagAddInvocationCount2() {
    Registry.get("java.completion.methods.use.tags").setValue(true, getTestRootDisposable());
    myFixture.configureByText("Test.java", """
      import java.util.HashSet;
      
      public abstract class SuperClass {
      
          void run() {
              HashSet<Object> objects = new HashSet<>();
              objects.le<caret>;
          }
      }
      """);
    LookupElement[] lookupElements = myFixture.complete(CompletionType.BASIC, 1);
    assertFalse(ContainerUtil.exists(lookupElements, t -> t.getLookupString().equals("size")));

    lookupElements = myFixture.complete(CompletionType.BASIC, 2);
    assertTrue(ContainerUtil.exists(lookupElements, t -> t.getLookupString().equals("size")));
  }

  @NeedsIndex.Full
  public void testTagAddInvocationRestart() {
    Registry.get("java.completion.methods.use.tags").setValue(true, getTestRootDisposable());
    myFixture.configureByText("Test.java", """
      import java.util.HashSet;
      
      public abstract class SuperClass {
      
          void run() {
              HashSet<Object> objects = new HashSet<>();
              objects.<caret>;
          }
      }
      """);
    myFixture.complete(CompletionType.BASIC, 1);
    myFixture.type("l");
    myFixture.type("e");
    myFixture.type("n");
    myFixture.type("g");
    myFixture.type("t");
    LookupElement element = ContainerUtil.find(myFixture.getLookupElements(), t -> t.getLookupString().equals("size"));
    assertNotNull(element);
    LookupElementPresentation presentation = new LookupElementPresentation();
    element.renderElement(presentation);
    assertEquals("size() Tag: length", presentation.getItemText());
    assertEquals("int", presentation.getTypeText());
  }

  @NeedsIndex.Full
  public void testResolveToSubclassMethod() {
    myFixture.configureByText("Test.java", """
      import java.util.List;

      public final class Complete {
        public static void main(String[] args) {
          SubClass instance;
              instance.<caret>
        }

        static class SuperClass<T> {
          public List<T> list(Object param) {return null;}
        }

        static class SubClass extends SuperClass<String> {
          @Override
          public List<String> list(Object paramName) {
            return super.list(paramName);
          }
        }

      }""");
    LookupElement[] elements = myFixture.completeBasic();
    assertNotNull(elements);
    LookupElement listElement = StreamEx.of(elements).collect(MoreCollectors.onlyOne(e -> e.getLookupString().equals("list"))).orElseThrow();
    LookupElementPresentation presentation = new LookupElementPresentation();
    listElement.renderElement(presentation);
    assertEquals("(Object paramName)", presentation.getTailText());
  }

  @NeedsIndex.Full
  public void testResolveToSubclassMethod2() {
    myFixture.configureByText("Test.java", """
      public final class Complete {
        public static void main(String[] args) {
          SubClass instance;
          instance.<caret>
        }
      
        static class SuperClass<T> {
          public List<? extends Object> list(String param) {
            return null;
          }
        }
      
        static class SubClass extends SuperClass<String> {
          @Override
          public List<String> list(String paramName) {
            return null;
          }
        }
      }""");
    LookupElement[] elements = myFixture.completeBasic();
    assertNotNull(elements);
    LookupElement listElement = StreamEx.of(elements).collect(MoreCollectors.onlyOne(e -> e.getLookupString().equals("list"))).orElseThrow();
    LookupElementPresentation presentation = new LookupElementPresentation();
    listElement.renderElement(presentation);
    assertEquals("(String paramName)", presentation.getTailText());
    assertEquals("List<String>", presentation.getTypeText());
  }

  @NeedsIndex.ForStandardLibrary
  public void testCompleteUnnamed() {
    myFixture.configureByText("Test.java", """
      class X {
        void test() {
          String _ = "hello";
          _.t<caret>
        }
      }
      """);
    LookupElement[] elements = myFixture.completeBasic();
    assertEquals(0, elements.length);
  }

  @NeedsIndex.ForStandardLibrary
  public void testSwitchUncompletedDefault() {
    myFixture.configureByText("Test.java", """
        class Test {
            void test(Integer o) {
                switch (o) {
                    d<caret>:
                        break;
                }
            }
        }
      """);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResult("""
        class Test {
            void test(Integer o) {
                switch (o) {
                    default:
                        <caret>
                        break;
                }
            }
        }
      """);  }

  @NeedsIndex.Full
  public void testNestedImplicitClass() {
    myFixture.configureByText("Test.java", """
        public static class NestedClass{
      
        }
      
        public static void main(String[] args) {
             NestedCla<caret>
        }
      """);
    myFixture.complete(CompletionType.BASIC);
    myFixture.checkResult("""
        public static class NestedClass{
      
        }
      
        public static void main(String[] args) {
             NestedClass
        }
      """);  }

  @NeedsIndex.Full
  public void testNestedQualifierImplicitClass() {
    myFixture.configureByText("Test.java", """
        public static class Nested {
            public static class Nested2ClassMore {
            }
        }
      
      
        public void main(String[] args) {
            Nested nested = new Nested();
        }
      
        public void t(Nested2ClassMo<caret> nested2) {
      
        }
      """);
    myFixture.complete(CompletionType.BASIC);
    myFixture.type('\n');
    myFixture.checkResult("""
        public static class Nested {
            public static class Nested2ClassMore {
            }
        }
      
      
        public void main(String[] args) {
            Nested nested = new Nested();
        }
      
        public void t(Nested.Nested2ClassMore nested2) {
      
        }
      """);  }

  public void testOuterVariableNotShadowedByPrivateField() {
    // IDEA-340271
    myFixture.configureByText("Test.java", """
      class Super {
        private int variable;
      }
      class Use {
        void test(int variable) {
          //noinspection ResultOfObjectAllocationIgnored
          new Super() {
            void m() {
              System.out.println(var<caret>);
            }
          };
        }
      }
      """);
    myFixture.completeBasic();
    myFixture.checkResult("""
      class Super {
        private int variable;
      }
      class Use {
        void test(int variable) {
          //noinspection ResultOfObjectAllocationIgnored
          new Super() {
            void m() {
              System.out.println(variable);
            }
          };
        }
      }
      """);
  }

  public void testOuterVariableNotShadowedByPrivateField2() {
    // IDEA-340271
    myFixture.configureByText("Test.java", """
      class C {
        class Super {
          private int variable;
        }
        class Use {
          void test(int variable) {
            //noinspection ResultOfObjectAllocationIgnored
            new Super() {
              void m() {
                System.out.println(var<caret>);
              }
            };
          }
        }
      }
      """);
    myFixture.completeBasic();
    myFixture.checkResult("""
      class C {
        class Super {
          private int variable;
        }
        class Use {
          void test(int variable) {
            //noinspection ResultOfObjectAllocationIgnored
            new Super() {
              void m() {
                System.out.println(variable);
              }
            };
          }
        }
      }
      """);
  }

  public void testCompletionWithBrokenClass() {
    myFixture.configureByText("UICallback.java", """
      public <caret>interface UICallback {
      }
      """);
    LookupElement[] elements = myFixture.completeBasic();
    for (LookupElement element : elements) {
      if (!element.getLookupString().equals("UICallback")) {
        continue;
      }
      selectItem(element);
      break;
    }

    myFixture.checkResult("""
      public UICallbackinterface UICallback {
      }
      """);
  }

  public void testNoSuggestionsAfterDotAtClassLevel() { doAntiTest(); }
  public void testNoSuggestionsAfterDotInParameter() { doAntiTest(); }

  public void testNoSuggestionsAfterDotWithLetter() {
    String path = "/" + getTestName(false) + ".java";
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(path, StringUtil.getShortName(path, '/')));
    myItems = myFixture.complete(CompletionType.BASIC, 0);
    checkResultByFile(getTestName(false) + ".java");
    assertEmpty(myItems);
    assertNull(getLookup());
  }

  public void testSuggestionsAfterDotAtClassLevel() {
    configureByTestName();
    myFixture.completeBasic();
    assertTrue(myFixture.getLookupElementStrings().contains("A"));
  }
}
