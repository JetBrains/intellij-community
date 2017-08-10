// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE.txt file.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase;
import com.intellij.codeInsight.completion.StaticallyImportable;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.SmartCompletionContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.util.containers.ContainerUtil;

public class SmartTypeCompletionTest extends LightFixtureCompletionTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/smartType/";
  }

  public void testParenAfterCast1() throws Exception {
    String path = "/parenAfterCast";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testParenAfterCast2() throws Exception {
    String path = "/parenAfterCast";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }


  public void testParenAfterCast3() throws Exception {
    String path = "/parenAfterCast";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testParenAfterCall1() throws Exception {
    String path = "/parenAfterCall";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testParenAfterCall2() throws Exception {
    String path = "/parenAfterCall";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testParenAfterCall3() throws Exception {
    String path = "/parenAfterCall";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testParenAfterCall4() throws Exception {
    String path = "/parenAfterCall";

    configureByFile(path + "/before4.java");
    checkResultByFile(path + "/after4.java");
  }

  public void testParenAfterCall5() throws Exception {
    String path = "/parenAfterCall";

    configureByFile(path + "/before5.java");
    checkResultByFile(path + "/after5.java");
  }

  public void testParenAfterCall6() throws Exception {
    String path = "/parenAfterCall";

    configureByFile(path + "/before6.java");
    checkResultByFile(path + "/after6.java");
  }
  
  public void testParenAfterCall1_SpaceWithinMethodCallParens() throws Exception {
    String path = "/parenAfterCall";

    myFixture.configureByFile(path + "/before1.java");
    getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    complete();
    checkResultByFile(path + "/after1_space.java");
  }

  public void testParenAfterIf1() throws Exception {
    String path = "/parenAfterIf";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testParenAfterIf2() throws Exception {
    String path = "/parenAfterIf";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testForceLookupForAbstractClasses() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before9.java");
    checkResultByFile(path + "/after9.java");
  }

  public void testAfterNew1() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before1.java");
    select();
    checkResultByFile(path + "/after1.java");
  }

  public void testAfterNew2() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before2.java");
    select();
    checkResultByFile(path + "/after2.java");
  }

  public void testAfterNew3() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before3.java");
    select();
    checkResultByFile(path + "/after3.java");
  }

  public void testAfterNew4() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before4.java");
    select();
    checkResultByFile(path + "/after4.java");
  }

  public void testAfterNew5() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before5.java");
    select();
    checkResultByFile(path + "/after5.java");
  }

  public void testAfterNew6() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before6.java");
    select();
    checkResultByFile(path + "/after6.java");
  }

  public void testAfterNew7() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before7.java");
    select();
    checkResultByFile(path + "/after7.java");
  }

  public void testAfterNew8() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before8.java");
    select();
    checkResultByFile(path + "/after8.java");
  }

  public void testAfterNew9() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before10.java");
    select();
    checkResultByFile(path + "/after10.java");
  }

  public void testAfterNew10() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before12.java");
    //select();
    checkResultByFile(path + "/after12.java");
  }

  public void testAfterNew11() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before13.java");
    //select();
    checkResultByFile(path + "/after13.java");
  }

  public void testAfterThrowNew1() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before14.java");
    //select();
    checkResultByFile(path + "/after14.java");
  }

  public void testAfterThrowNew2() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before15.java");
    select();
    checkResultByFile(path + "/after15.java");
  }

  public void testAfterThrowNew3() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/before16.java");
    //select();
    checkResultByFile(path + "/after16.java");
  }

  public void testCastInThrow() throws Exception { doTest(); }
  public void testNonExistentGenericAfterNew() throws Exception { doTest('\n'); }

  public void testParenAfterNewWithinInnerExpr() throws Exception {
    String path = "/afterNew";

    configureByFile(path + "/LastArgInInnerNewBefore.java");
    checkResultByFile(path + "/LastArgInInnerNewAfter.java");

    //configureByFile(path + "/LastArgInInnerNewBefore2.java");
    //performAction();
    //checkResultByFile(path + "/LastArgInInnerNewAfter2.java");

    configureByFile(path + "/LastArgInInnerNewBefore3.java");
    checkResultByFile(path + "/LastArgInInnerNewAfter3.java");

    configureByFile(path + "/LastArgInInnerNewBefore4.java");
    checkResultByFile(path + "/LastArgInInnerNewAfter4.java");
  }

  public void testReturn1() throws Exception{
    String path = "/return";
    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testReturn2() throws Exception{
    String path = "/return";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testReturn3() throws Exception{
    String path = "/return";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testGenerics1() throws Exception {
    String path = "/generics";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testGenerics2() throws Exception {
    String path = "/generics";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testGenerics3() throws Exception {
    String path = "/generics";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testGenerics4() throws Exception {
    String path = "/generics";

    configureByFile(path + "/before4.java");
    checkResultByFile(path + "/after4.java");
  }

  public void testGenerics5() throws Exception {
    String path = "/generics";

    configureByFile(path + "/before5.java");
    checkResultByFile(path + "/after5.java");
  }

  public void testAfterInstanceOf1() throws Exception {
    String path = "/afterInstanceOf";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testAfterInstanceOf2() throws Exception {
    String path = "/afterInstanceOf";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testInsideCatch() { doTest(); }
  public void testInsideCatchFinal() { doTest(); }
  public void testInsideCatchWithoutThrow() { doTest(); }

  public void testGenerics6() throws Exception {
    String path = "/generics";

    configureByFile(path + "/before6.java");
    checkResultByFile(path + "/after6.java");
  }

  public void testWildcardNew1() throws Exception {
    String path = "/generics";

    configureByFile(path + "/before7.java");
    checkResultByFile(path + "/after7.java");
  }

  public void testWildcardNew2() throws Exception {
    String path = "/generics";

    configureByFile(path + "/before8.java");
    checkResultByFile(path + "/after8.java");
  }

  public void testWildcardEliminated() throws Exception {
    String path = "/generics";

    configureByFile(path + "/before9.java");
    selectItem(myItems[1]);
    checkResultByFile(path + "/after9.java");
  }

  public void testBug1() throws Exception { doTest(); }

  public void testQualifiedThis() throws Exception { doTest(); }

  public void testBug2() throws Exception {
    configureByFile("/Bug2.java");
  }


  public void testSillyAssignment1() throws Exception {
    configureByFile("/Silly1.java");
    checkResultByFile("/Silly1.java");
  }

  public void testVarargs1() throws Exception { doTest('\n'); }

  public void testEnumConstInSwitch() throws Exception { doTest(); }

  public void testEnumConstInSwitchOutside() throws Exception { doTest(); }

  public void testIntConstInSwitch() throws Exception { doTest(); }

  public void testDoubleEmptyArray() throws Exception {
    configureByTestName();
    checkResultByFile("/"+getTestName(false) + ".java");
    assertEquals(2, myItems.length);
  }

  public void testCollectionsEmptySetInMethodCall() throws Throwable { doTest(); }

  public void testCollectionsEmptySetInTernary() throws Throwable { doTest(); }

  public void testStringConstantInAnno() throws Throwable { doTest(); }

  public void testCollectionsEmptySetInTernary2() throws Throwable { doTest(); }

  public void testConstructorOnSeparateLineInMethodCall() throws Throwable { doTest(); }

  public void testConstructorWithExistingParens() throws Throwable { doTest(); }

  public void testMethodAnnotationNamedParameter() throws Throwable { doTest(); }
  
  public void testInheritedClass() throws Throwable { doTest(); }

  public void testClassLiteralInAnno1() throws Throwable { doTest(); }

  public void testMeaninglessExplicitWildcardParam() throws Throwable { doTest(); }

  public void testExplicitWildcardArrayParam() throws Throwable { doTest(); }

  public void testCatchInAnonymous() throws Throwable { doTest(); }

  public void testThrowRuntimeException() throws Throwable { doTest(); }

  public void testParameterizedConstructor() throws Throwable { doTest(); }

  public void testNewInnerClassNameShortPrefix() throws Throwable { doTest('\n'); }

  public void testNewInnerOfParameterizedClass() throws Throwable { doTest(); }

  public void testQualifiedThisInAnonymousConstructor() throws Throwable { doTest(); }

  public void testExceptionTwice() throws Throwable { doTest(); }

  public void testExceptionTwice2() throws Throwable { doTest(); }

  public void testNewInnerRunnable() throws Throwable { doTest(); }

  public void testArrayAccessIndex() throws Throwable { doTest(); }

  public void testThrowExceptionConstructor() throws Throwable { doTest('\n'); }

  public void testJavadocThrows() throws Throwable { doTest(); }

  public void testMethodThrows() throws Throwable { doTest(); }

  public void testDoNotExcludeAssignedVariable() throws Throwable { doTest(); }

  public void testArrayIndexTailType() throws Throwable { doTest(); }

  public void testPrivateOverloads() throws Throwable { doTest(); }

  public void testPolyadicExpression() throws Throwable { doTest(); }

  public void testCastAutoboxing() throws Throwable {
    doItemTest();
  }
  public void testCastAutoboxing2() throws Throwable {
    doItemTest();
  }
  public void testCastAutoboxing3() throws Throwable {
    doItemTest();
  }
  public void testCastWildcards() throws Throwable { doTest(); }

  public void testNoSecondMethodTypeArguments() throws Throwable { doTest(Lookup.REPLACE_SELECT_CHAR); }

  public void testNoFieldsInSuperConstructorCall() throws Throwable { doTest(); }

  public void testChainMethodsInSuperConstructorCall() throws Throwable { doTest(); }

  public void testNoUninitializedFieldsInConstructor() throws Throwable {
    configureByTestName();
    assertStringItems("aac", "aab", "hashCode");
  }
  public void testFieldsSetInAnotherConstructor() throws Throwable { doTest(); }
  public void testFieldsSetAbove() throws Throwable { doTest(); }

  public void testHonorSelection() throws Throwable {
    configureByTestName();
    select();
    checkResultByTestName();
  }

  public void testTypeParametersInheritors() throws Throwable {
    configureByTestName();
    assertStringItems("Foo", "Bar", "Goo");
    select();
    checkResultByTestName();
  }

  public void testVoidExpectedType() throws Throwable {
    configureByTestName();
    assertStringItems("notify", "notifyAll", "wait", "wait", "wait", "equals", "hashCode", "toString", "getClass");
    type("eq");
    assertEquals("equals", assertOneElement(getLookup().getItems()).getLookupString());
    select();
    checkResultByTestName();
  }

  public void testDoubleSemicolonPenetration() throws Throwable { doTest(); }

  public void testTypeParametersInheritorsComma() throws Throwable { doTest(); }

  public void testTypeParametersInheritorsInExpression() throws Throwable { doTest(); }

  //do we need to see all Object inheritors at all?
  public void _testTypeParametersObjectInheritors() throws Throwable { doTest(); }

  public void testDoubleThis() throws Throwable {
    doTest();
    assertNull(myItems);
  }

  public void testSmartFinish() throws Throwable { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR); }

  public void testSillyAssignmentInTernary() throws Throwable { doTest(); }

  public void testSameFieldInAnotherObject() throws Throwable { doTest(); }

  public void testUnqualifiedConstantInSwitch() throws Throwable { doTest(); }

  public void testAmbiguousConstant() throws Throwable { doTest(); }

  public void testSameNamedFieldAndLocal() throws Throwable { doTest(); }

  public void testNoTailWhenNoPairBracket() throws Throwable { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  public void testNoTailWhenNoPairBracket2() throws Throwable { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  public void testAnonymousNoPairBracket() throws Throwable { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  private void doTestNoPairBracket(final char c) throws Exception {
    boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;
    try {
      doTest(c);
    }
    finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = old;
    }
  }

  public void testNoConstructorTailWhenNoPairBracket() throws Throwable { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  public void testConstructorNoPairBracketSemicolon() throws Throwable { doTestNoPairBracket(';'); }

  public void testMethodNoPairBracketComma() throws Throwable { doTestNoPairBracket(','); }

  public void testAbstractClassTwice() throws Throwable {
    configureByTestName();
    assertOneElement(myItems);
  }

  public void testConstantTwice() throws Throwable { doTest(); }

  public void testConstantTwice2() throws Throwable {
    configureByTestName();
    assertEquals(2, myItems.length);
  }

  public void testNoKeyConstant() throws Throwable {
    configureByTestName();
    assertStringItems("A_KEY", "create");
  }

  public void testUserDataListAddAll() throws Throwable {
    doTest();
  }

  public void testStaticSubclass() throws Throwable {
    doTest();
  }

  public void testMethodCallDot() throws Throwable { doTest('\n'); }
  public void testNegateVariable() throws Throwable { doTest(); }

  public void testExclamationMethodFinish() throws Throwable { doTest('!'); }
  public void testExclamationVariableFinish() throws Throwable { doTest('!'); }
  public void testExclamationStaticFieldFinish() throws Throwable { doTest('!'); }
  public void testExclamationFinishNonBoolean() throws Throwable { doTest('!'); }

  public void testExcludeDeclaredConstant() throws Throwable { doTest(); }

  public void testTabMethodInBinaryExpression() throws Throwable { doTest('\t'); }

  public void testIfConditionBinaryExpression() throws Throwable { doTest(); }

  public void testDelegationToParent() throws Throwable { doTest('\t'); }

  public void testBeforeBinaryExpressionInMethodCall() throws Throwable { doTest(); }

  public void testAssignableToAfterCast() throws Throwable { doTest(); }

  public void testInstanceMethodParametersFromStaticContext() throws Throwable { doTest(); }

  public void testInstanceMethodParametersFromStaticContext2() throws Throwable { doTest(); }

  public void testBeforeCastToArray() throws Throwable { doTest(); }

  public void testHidingFields() throws Throwable { doTest(); }

  public void testVoidCast() throws Throwable { doAntiTest(); }

  public void testIntPlusLongNotDouble() throws Throwable { doTest(); }

  public void testNestedAssignments() throws Throwable { doTest(); }

  public void testAfterNewInTernary() throws Throwable { doTest(); }

  public void testSuggestAnythingWhenWildcardExpected() throws Throwable {
    configureByTestName();
    assertStringItems("X", "Y", "Z");
  }

  public void testNewVararg() throws Throwable {
    configureByTestName();
    assertStringItems("Foo", "Foo", "Foo");
    assertEquals("{...} (default package)", LookupElementPresentation.renderElement(myItems[0]).getTailText());
    assertEquals("[] (default package)", LookupElementPresentation.renderElement(myItems[1]).getTailText());
    assertEquals("[]{...} (default package)", LookupElementPresentation.renderElement(myItems[2]).getTailText());
  }

  public void testNewVararg2() throws Throwable {
    configureByTestName();
    assertStringItems("String", "String", "String");
    assertEquals(" (java.lang)", LookupElementPresentation.renderElement(myItems[0]).getTailText());
    assertEquals("[] (java.lang)", LookupElementPresentation.renderElement(myItems[1]).getTailText());
    assertEquals("[]{...} (java.lang)", LookupElementPresentation.renderElement(myItems[2]).getTailText());
  }

  public void testNewByteArray() {
    configureByTestName();
    assertStringItems("byte");
    assertEquals("[]", LookupElementPresentation.renderElement(myItems[0]).getTailText());
  }

  public void testNewByteArray2() {
    configureByTestName();
    assertStringItems("byte", "byte");
    assertEquals("[]", LookupElementPresentation.renderElement(myItems[0]).getTailText());
    assertEquals("[]{...}", LookupElementPresentation.renderElement(myItems[1]).getTailText());
  }

  public void testInsideStringLiteral() throws Throwable { doAntiTest(); }

  public void testDefaultAnnoParam() throws Throwable { doTest(); }

  public void testNewWithTypeParameterErasure() throws Throwable { doTest(); }

  public void testEverythingDoubles() throws Throwable {
    configureByTestName();
    assertStringItems("hashCode", "indexOf", "lastIndexOf", "size");
  }

  public void testNonStaticInnerClass() throws Throwable {
    configureByTestName();
    assertEmpty(myItems);
    checkResultByFile("/" + getTestName(false) + ".java");
  }

  //todo 2nd completion
  public void _testDefaultAnnoParam2() throws Throwable { doTest(); }
  
  public void testAnnotationValue() throws Throwable {doTest(); }

  public void testLiveTemplate() throws Throwable {
    final Template template = TemplateManager.getInstance(getProject()).createTemplate("foo", "zzz");
    template.addTextSegment("FooFactory.createFoo()");
    final SmartCompletionContextType completionContextType =
      ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), SmartCompletionContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(completionContextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
    doTest();
  }

  public void testInThisExpression() throws Throwable { doTest(); }

  public void testSuggestNull() throws Throwable { doTest(); }

  public void testNoNullAfterDot() throws Throwable {
    configureByTestName();
    assertEmpty(myItems);
    checkResultByFile("/" + getTestName(false) + ".java");
  }

  public void testDefaultAnnoMethodValue() throws Throwable { doTest(); }

  public void testNewAnonymousFunction() throws Throwable { doTest(); }

  public void testNewRunnableInsideMethod() throws Throwable {
    CommonCodeStyleSettings settings = getCodeStyleSettings();
    boolean lParenOnNextLine = settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE;
    try {
      settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
      doTest();
    } finally {
      settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = lParenOnNextLine;
    }
  }

  public void testNewRunnableInsideMethodMultiParams() throws Throwable {
    CommonCodeStyleSettings settings = getCodeStyleSettings();
    boolean lParenOnNextLine = settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE;
    boolean rParenOnNextLine = settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE;
    try {
      settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
      settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = true;
      doTest();
    } finally {
      settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = lParenOnNextLine;
      settings.CALL_PARAMETERS_RPAREN_ON_NEXT_LINE = rParenOnNextLine;
    }
  }

  public void testUseIntConstantsFromTargetClass() throws Throwable { doTest(); }
  public void testUseObjectConstantsFromTargetClass() { doTest(); }
  public void testUseIntConstantsFromTargetClassReturnValue() throws Throwable { doTest(); }
  public void testUseIntConstantsFromConstructedClass() throws Throwable { doTest(); }
  public void testUseIntConstantsInPlus() throws Throwable { doTest(); }
  public void testUseIntConstantsInOr() throws Throwable { doTest(); }

  public void testExtraSemicolonAfterMethodParam() throws Throwable {
    getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    doTest();
  }

  public void testNoSemicolonInsideParentheses() { doTest(); }

  public void testAssignFromTheSameFieldOfAnotherObject() throws Throwable {
    doTest();
  }

  public void testTailAfterInstanceOf() throws Throwable {
    doTest();
  }

  public void testReplaceWholeReferenceChain() throws Throwable { doTest(Lookup.REPLACE_SELECT_CHAR); }

  public void testDoubleTrueInOverloadedMethodCall() throws Throwable { doTest(Lookup.REPLACE_SELECT_CHAR); }

  public void testMethodColon() throws Exception { doFirstItemTest(':'); }
  public void testVariableColon() throws Exception { doFirstItemTest(':'); }
  public void testConditionalColonOnNextLine() { doFirstItemTest(':'); }

  private void doFirstItemTest(char c) {
    configureByTestName();
    select(c);
    checkResultByTestName();
  }

  public void testOneElementArray() throws Throwable { doTest(); }

  public void testCastToArray() throws Throwable { doTest(); }

  public void testCommaDoublePenetration() throws Throwable {
    doFirstItemTest(',');
  }

  public void testSuperMethodArguments() throws Throwable {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(1));
    select();
    checkResultByTestName();
  }

  public void testDelegateMethodArguments() throws Throwable {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(1));
    select();
    checkResultByTestName();
  }

  public void testSameMethodArgumentsInIf() throws Throwable {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(1));
    select();
    checkResultByTestName();
  }

  public void testSuperConstructorArguments() throws Throwable {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    select();
    checkResultByTestName();
  }

  public void testSameNamedArguments() throws Throwable {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(4));
    select();
    checkResultByTestName();
  }

  public void testSameNamedArgumentsDelegation() throws Throwable {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(1));
    select();
    checkResultByTestName();
  }

  public void testSameSignatureWithGenerics() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "i", "z", "zz", "i, z, zz");
  }

  public void testSuggestTypeParametersInTypeArgumentList() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "T", "String");
  }

  public void testWrongAnonymous() throws Throwable {
    configureByTestName();
    select();
    checkResultByTestName();
  }

  public void testAfterNewWithGenerics() throws Exception {
    doActionTest();
  }

  public void testClassLiteral() throws Exception {
    doActionTest();
    assertStringItems("String.class");

    LookupElementPresentation p = new LookupElementPresentation();
    myFixture.getLookupElements()[0].renderElement(p);
    assertEquals("String.class", p.getItemText());
    assertEquals(" (java.lang)", p.getTailText());
    assertNull(p.getTypeText());
  }
  public void testNoClassLiteral() throws Exception {
    doActionTest();
    assertStringItems("Object.class", "getClass", "forName", "forName");
  }

  public void testClassLiteralInAnno2() throws Throwable {
    doItemTest();
  }

  public void testClassLiteralInheritors() throws Throwable {
    doItemTest();
  }

  public void testInsertOverride() throws Exception {
    JavaCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    styleSettings.INSERT_OVERRIDE_ANNOTATION = true;
    doItemTest();
  }

  public void testForeach() throws Exception {
    doActionTest();
  }

  public void testIDEADEV2626() throws Exception {
    doItemTest();
  }

  public void testDontSuggestWildcardGenerics() { doItemTest(); }

  public void testCastWith2TypeParameters() throws Throwable { doTest(); }
  public void testClassLiteralInArrayAnnoInitializer() throws Throwable { doTest(); }
  public void testClassLiteralInArrayAnnoInitializer2() throws Throwable { doTest(); }

  public void testAnnotation() throws Exception {
    configureByTestName();
    assertStringItems("ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR",
                      "ElementType.FIELD", "ElementType.LOCAL_VARIABLE",
                      "ElementType.METHOD", "ElementType.PACKAGE", "ElementType.PARAMETER",
                      "ElementType.TYPE" /*, "ElementType.TYPE_PARAMETER", "ElementType.TYPE_USE"*/);
  }

  public void testAnnotation2() throws Exception {
    configureByTestName();
    assertStringItems("RetentionPolicy.CLASS", "RetentionPolicy.RUNTIME", "RetentionPolicy.SOURCE");
  }
  public void testAnnotation2_2() throws Exception {
    configureByTestName();
    assertSameElements(myFixture.getLookupElementStrings(), "RetentionPolicy.CLASS", "RetentionPolicy.SOURCE", "RetentionPolicy.RUNTIME");
  }

  public void testAnnotation3() throws Exception {
    doTest();
  }

  public void testAnnotation3_2() throws Exception {
    doTest();
  }

  public void testAnnotation4() throws Exception {
    configureByTestName();
    checkResultByTestName();

    assertStringItems("false", "true");
  }

  public void testAnnotation5() throws Exception {
    configureByTestName();
    checkResultByTestName();

    assertStringItems("CONNECTION", "NO_CONNECTION");
  }

  public void testAnnotation6() throws Exception {
    configureByTestName();
  
    assertStringItems("ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR",
                      "ElementType.FIELD", "ElementType.LOCAL_VARIABLE",
                      "ElementType.METHOD", "ElementType.PACKAGE", "ElementType.PARAMETER",
                      "ElementType.TYPE"/*, "ElementType.TYPE_PARAMETER", "ElementType.TYPE_USE"*/);
  }

  public void testArrayClone() throws Exception {
    doTest();
  }

  public void testIDEADEV5150() throws Exception {
    doTest('\n');
  }

  public void testIDEADEV7835() throws Exception {
    doTest();
  }

  public void testTypeArgs1() throws Exception {
    doTest();
  }

  public void testTypeArgs2() throws Exception {
    doTest();
  }
  public void testTypeArgsOverwrite() { doTest(); }

  public void testIfConditionExpectedType() throws Exception { doTest(); }

  public void testUnboundTypeArgs() throws Exception { doTest(); }
  public void testUnboundTypeArgs2() throws Exception { doTest(); }
  public void testSameTypeArg() throws Exception { doTest(); }

  public void testIDEADEV2668() throws Exception {
    doTest();
  }

  public void testExcessiveTail() throws Exception { doTest(); }
  public void testSeveralTypeArguments() throws Exception { doTest(); }
  public void testSeveralTypeArgumentsSomeUnknown() throws Exception { doTest(); }

  public void testExtendsInTypeCast() throws Exception {
    doTest();
  }

  public void testTabMethodCall() throws Exception {
    doFirstItemTest(Lookup.REPLACE_SELECT_CHAR);
  }

  public void testConstructorArgsSmartEnter() throws Exception { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR); }

  public void testIDEADEV13148() throws Exception {
    configureByFile("/IDEADEV13148.java");
    assertStringItems("false", "true"); //todo don't suggest boolean literals in synchronized
  }

  public void testSuggestNames() throws Exception {
    configureByTestName();
    assertStringItems("arrayList", "list");
  }

  public void testOverloadedMethods() throws Throwable {
    doTest();
  }

  public void testNoCommaBeforeVarargs() throws Throwable { doTest(); }

  public void testEnumField() throws Throwable {
    doItemTest();
  }

  public void testEnumField1() throws Exception {
    configureByTestName();
    checkResultByTestName();
    assertEquals(4, myItems.length);
  }

  public void testInsertTypeParametersOnImporting() throws Throwable { doTest('\n'); }

  public void testEmptyListInReturn() throws Throwable { doItemTest(); }

  public void testEmptyListInReturn2() throws Throwable { doTest(); }

  public void testEmptyListInReturnTernary() throws Throwable { doItemTest(); }

  public void testEmptyListBeforeSemicolon() throws Throwable { doItemTest(); }

  public void testEmptyListWithCollectionsPrefix() throws Throwable { doItemTest(); }

  public void testForeachLoopVariableInIterableExpression() throws Throwable { doAntiTest(); }

  public void testStaticallyImportedMagicMethod() throws Throwable {
    configureByTestName();
    assertStringItems("foo");
    selectItem(myItems[0], '\t');
    checkResultByTestName();
  }

  public void _testCallVarargArgument() throws Throwable { doTest(); }

  public void testTabToReplaceClassKeyword() throws Throwable {
    configureByTestName();
    selectItem(myItems[0], Lookup.REPLACE_SELECT_CHAR);
    checkResultByTestName();
  }

  public void testNoTypeParametersForToArray() throws Throwable {
    doTest();
  }

  public void testStaticallyImportedField() throws Throwable { doTest('\n'); }
  public void testSiblingOfAStaticallyImportedField() throws Throwable { doTest(); }
  public void testPrimitiveArrayClassInMethod() throws Throwable { doTest(); }
  public void testPrimitiveClassInAnno() throws Throwable { doTest(); }
  public void testNewInnerClassOfSuper() throws Throwable { doTest(); }
  public void testAssertThatMatcher() throws Throwable { doTest(); }

  public void testInferFromCall() throws Throwable {
    doTest();
  }

  public void testInferFromCall1() throws Throwable {
    doTest();
  }

  public void testCastToParameterizedType() throws Throwable { doActionTest(); }

  public void testInnerEnumInMethod() throws Throwable {
    doItemTest();
  }

  public void testEnumAsDefaultAnnotationParam() throws Throwable { doTest(); }
  public void testBreakLabel() throws Throwable { doTest(); }

  public void testNewAbstractInsideAnonymous() throws Throwable { doTest(); }

  public void testFilterPrivateConstructors() throws Throwable { doTest(); }

  public void testExplicitMethodTypeParametersQualify() throws Throwable { doTest(); }
  public void testExplicitMethodTypeParametersOverZealous() throws Throwable { doTest(); }
  public void testExplicitMethodTypeParametersFromSuperClass() throws Throwable { doTest(); }

  public void testWildcardedInstanceof() throws Throwable { doTest(); }
  public void testWildcardedInstanceof2() throws Throwable { doTest(); }
  public void testWildcardedInstanceof3() throws Throwable { doTest(); }

  public void testCheckStaticImportsType() throws Throwable { doAntiTest(); }
  public void testThisFieldAssignedToItself() throws Throwable { doAntiTest(); }

  public void testCaseMissingEnumValue() throws Throwable { doTest(); }
  public void testCaseMissingEnumValue2() throws Throwable { doTest(); }
  
  public void testNoHiddenParameter() { doTest(); }

  public void testTypeVariableInstanceOf() throws Throwable {
    configureByTestName();
    performAction();
    assertStringItems("Bar", "Goo");
  }

  public void testAutoImportExpectedType() throws Throwable {
    boolean old = CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY;
    CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = true;
    try {
      configureByTestName();
      performAction();
      myFixture.assertPreferredCompletionItems(1, "List", "ArrayList", "AbstractList");
    }
    finally {
      CodeInsightSettings.getInstance().ADD_UNAMBIGIOUS_IMPORTS_ON_THE_FLY = old;
    }
  }

  public void testNoWrongSubstitutorFromStats() throws Throwable {
    doTest();
    FileDocumentManager.getInstance().saveDocument(myFixture.getEditor().getDocument());
    doTest(); // stats are changed now
  }

  public void testCommonPrefixWithSelection() throws Throwable {
    doItemTest();
  }

  public void testNewAbstractClassWithConstructorArgs() throws Throwable {
    doItemTest();
  }

  public void testArrayInitializerBeforeVarargs() throws Throwable { doTest(); }
  public void testDuplicateMembersFromSuperClass() throws Throwable { doTest(); }
  public void testInnerAfterNew() throws Throwable { doTest(); }
  public void testOuterAfterNew() { doTest(); }
  public void testEverythingInStringConcatenation() throws Throwable { doTest(); }
  public void testGetClassWhenClassExpected() { doTest(); }

  public void testMemberImportStatically() {
    configureByTestName();
    StaticallyImportable item = myItems[0].as(StaticallyImportable.CLASS_CONDITION_KEY);
    assertNotNull(item);
    assertTrue(item.canBeImported());
    assertTrue(myItems[1].as(StaticallyImportable.CLASS_CONDITION_KEY).canBeImported());
    item.setShouldBeImported(true);
    type('\n');
    checkResultByTestName();
  }

  public void testNoNewEnum() throws Throwable {
    configureByTestName();
    assertStringItems("Foo");
  }

  public void testDuplicateMembersFromSuperClassInAnotherFile() throws Throwable {
    myFixture.addClass("class Super { public static final Super FOO = null; }");
    doTest();
  }

  public void testInsideGenericClassLiteral() throws Throwable {
    configureByTestName();
    assertStringItems("String.class", "StringBuffer.class", "StringBuilder.class");
  }

  public void testArrayAnnoParameter() throws Throwable {
    doActionTest();
  }

  public void testInnerClassImports() throws Throwable {
    JavaCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    settings.INSERT_INNER_CLASS_IMPORTS = true;
    try {
      myFixture.addClass("package java.awt.geom; public class Point2D { public static class Double {} }");
      doActionTest();
    }
    finally {
      settings.INSERT_INNER_CLASS_IMPORTS = false;
    }
  }

  public void testCastWithGenerics() throws Throwable {
    doActionTest();
  }

  public void testInnerEnum() throws Exception {
    configureByTestName();

    getLookup().setCurrentItem(ContainerUtil.find(myItems, lookupItem -> "Bar.Fubar.Bar".equals(lookupItem.getLookupString())));
    select('\n');
    checkResultByTestName();
  }

  public void testQualifiedAfterNew() throws Exception {
    myFixture.addClass("package foo; public interface Foo<T> {}");
    myFixture.addClass("package bar; public class Bar implements foo.Foo {}");
    doTest();
  }
  public void testAfterQualifiedNew() throws Exception {
    myFixture.addClass("class Aa { public class B { } }");
    doTest();
  }

  public void testTabAfterNew() throws Exception {
    doFirstItemTest('\t');
  }

  public void testSuggestMethodReturnType() { 
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "Serializable", "CharSequence", "Object");
  }

  public void testSuggestCastReturnTypeByCalledMethod() { doTest(); }

  public void testOnlyInterfacesInImplements() { doTest(); }

  public void testNonStaticField() throws Exception { doAntiTest(); }

  public void testLocalClassInExpectedTypeArguments() { doTest(); }

  private void doActionTest() {
    configureByTestName();
    checkResultByTestName();
  }

  private void doItemTest() {
    doFirstItemTest('\n');
  }

  private void performAction() {
    complete();
  }

  private void doTest() {
    doTest(Lookup.NORMAL_SELECT_CHAR);
  }

  private void doTest(final char c) {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION;
    if (c != Lookup.NORMAL_SELECT_CHAR) {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = false;
    }

    try {
      configureByTestName();
      if (myItems != null) {
        select(c);
      }
      checkResultByTestName();
    }
    finally {
      if (c != Lookup.NORMAL_SELECT_CHAR) {
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = old;
      }
    }

  }

  private void checkResultByTestName() {
    checkResultByFile("/" + getTestName(false) + "-out.java");
  }

  @Override
  protected void complete() {
    myItems = myFixture.complete(CompletionType.SMART);
  }

  private void select() {
    select(Lookup.NORMAL_SELECT_CHAR);
  }

  private void select(final char c) {
    final Lookup lookup = getLookup();
    if (lookup != null) {
      selectItem(lookup.getCurrentItem(), c);
    }
  }

  public void testSpaceAfterCommaInMethodCall() {
    getCodeStyleSettings().SPACE_AFTER_COMMA = false;
    doTest(',');
  }

  private CommonCodeStyleSettings getCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  public void testOnlyCompatibleTypes() {
    configureByTestName();
    assertOrderedEquals(myFixture.getLookupElementStrings(), "get2");
  }

  public void testQualifyOuterClassCall() { doActionTest(); }

  public void testExpressionSubtypesInCast() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "String", "StringBuffer", "StringBuilder");
  }

  public void testStaticBuilder() { doTest(); }
  public void testStaticBuilderWithArguments() { doTest(); }
  public void testStaticBuilderWithInterfaceAndGenerics() { doTest(); }

  public void testStaticBuilderWithGenerics() {
    configureByTestName();
    assertEquals("Map.builder().get(...)", LookupElementPresentation.renderElement(myItems[0]).getItemText());
    myFixture.type('\t');
    checkResultByTestName();
  }

  public void testFreeGenericsAfterClassLiteral() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "String.class", "tryCast");
  }

  public void testNewHashMapTypeArguments() { doTest(); }
  public void testNewMapTypeArguments() { doTest(); }

}
