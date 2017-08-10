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

  public void testParenAfterCast1() {
    String path = "/parenAfterCast";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testParenAfterCast2() {
    String path = "/parenAfterCast";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }


  public void testParenAfterCast3() {
    String path = "/parenAfterCast";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testParenAfterCall1() {
    String path = "/parenAfterCall";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testParenAfterCall2() {
    String path = "/parenAfterCall";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testParenAfterCall3() {
    String path = "/parenAfterCall";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testParenAfterCall4() {
    String path = "/parenAfterCall";

    configureByFile(path + "/before4.java");
    checkResultByFile(path + "/after4.java");
  }

  public void testParenAfterCall5() {
    String path = "/parenAfterCall";

    configureByFile(path + "/before5.java");
    checkResultByFile(path + "/after5.java");
  }

  public void testParenAfterCall6() {
    String path = "/parenAfterCall";

    configureByFile(path + "/before6.java");
    checkResultByFile(path + "/after6.java");
  }
  
  public void testParenAfterCall1_SpaceWithinMethodCallParens() {
    String path = "/parenAfterCall";

    myFixture.configureByFile(path + "/before1.java");
    getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    complete();
    checkResultByFile(path + "/after1_space.java");
  }

  public void testParenAfterIf1() {
    String path = "/parenAfterIf";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testParenAfterIf2() {
    String path = "/parenAfterIf";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testForceLookupForAbstractClasses() {
    String path = "/afterNew";

    configureByFile(path + "/before9.java");
    checkResultByFile(path + "/after9.java");
  }

  public void testAfterNew1() {
    String path = "/afterNew";

    configureByFile(path + "/before1.java");
    select();
    checkResultByFile(path + "/after1.java");
  }

  public void testAfterNew2() {
    String path = "/afterNew";

    configureByFile(path + "/before2.java");
    select();
    checkResultByFile(path + "/after2.java");
  }

  public void testAfterNew3() {
    String path = "/afterNew";

    configureByFile(path + "/before3.java");
    select();
    checkResultByFile(path + "/after3.java");
  }

  public void testAfterNew4() {
    String path = "/afterNew";

    configureByFile(path + "/before4.java");
    select();
    checkResultByFile(path + "/after4.java");
  }

  public void testAfterNew5() {
    String path = "/afterNew";

    configureByFile(path + "/before5.java");
    select();
    checkResultByFile(path + "/after5.java");
  }

  public void testAfterNew6() {
    String path = "/afterNew";

    configureByFile(path + "/before6.java");
    select();
    checkResultByFile(path + "/after6.java");
  }

  public void testAfterNew7() {
    String path = "/afterNew";

    configureByFile(path + "/before7.java");
    select();
    checkResultByFile(path + "/after7.java");
  }

  public void testAfterNew8() {
    String path = "/afterNew";

    configureByFile(path + "/before8.java");
    select();
    checkResultByFile(path + "/after8.java");
  }

  public void testAfterNew9() {
    String path = "/afterNew";

    configureByFile(path + "/before10.java");
    select();
    checkResultByFile(path + "/after10.java");
  }

  public void testAfterNew10() {
    String path = "/afterNew";

    configureByFile(path + "/before12.java");
    //select();
    checkResultByFile(path + "/after12.java");
  }

  public void testAfterNew11() {
    String path = "/afterNew";

    configureByFile(path + "/before13.java");
    //select();
    checkResultByFile(path + "/after13.java");
  }

  public void testAfterThrowNew1() {
    String path = "/afterNew";

    configureByFile(path + "/before14.java");
    //select();
    checkResultByFile(path + "/after14.java");
  }

  public void testAfterThrowNew2() {
    String path = "/afterNew";

    configureByFile(path + "/before15.java");
    select();
    checkResultByFile(path + "/after15.java");
  }

  public void testAfterThrowNew3() {
    String path = "/afterNew";

    configureByFile(path + "/before16.java");
    //select();
    checkResultByFile(path + "/after16.java");
  }

  public void testCastInThrow() { doTest(); }
  public void testNonExistentGenericAfterNew() { doTest('\n'); }

  public void testParenAfterNewWithinInnerExpr() {
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

  public void testReturn1() {
    String path = "/return";
    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testReturn2() {
    String path = "/return";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testReturn3() {
    String path = "/return";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testGenerics1() {
    String path = "/generics";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testGenerics2() {
    String path = "/generics";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testGenerics3() {
    String path = "/generics";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testGenerics4() {
    String path = "/generics";

    configureByFile(path + "/before4.java");
    checkResultByFile(path + "/after4.java");
  }

  public void testGenerics5() {
    String path = "/generics";

    configureByFile(path + "/before5.java");
    checkResultByFile(path + "/after5.java");
  }

  public void testAfterInstanceOf1() {
    String path = "/afterInstanceOf";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testAfterInstanceOf2() {
    String path = "/afterInstanceOf";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testInsideCatch() { doTest(); }
  public void testInsideCatchFinal() { doTest(); }
  public void testInsideCatchWithoutThrow() { doTest(); }

  public void testGenerics6() {
    String path = "/generics";

    configureByFile(path + "/before6.java");
    checkResultByFile(path + "/after6.java");
  }

  public void testWildcardNew1() {
    String path = "/generics";

    configureByFile(path + "/before7.java");
    checkResultByFile(path + "/after7.java");
  }

  public void testWildcardNew2() {
    String path = "/generics";

    configureByFile(path + "/before8.java");
    checkResultByFile(path + "/after8.java");
  }

  public void testWildcardEliminated() {
    String path = "/generics";

    configureByFile(path + "/before9.java");
    selectItem(myItems[1]);
    checkResultByFile(path + "/after9.java");
  }

  public void testBug1() { doTest(); }

  public void testQualifiedThis() { doTest(); }

  public void testBug2() {
    configureByFile("/Bug2.java");
  }


  public void testSillyAssignment1() {
    configureByFile("/Silly1.java");
    checkResultByFile("/Silly1.java");
  }

  public void testVarargs1() { doTest('\n'); }

  public void testEnumConstInSwitch() { doTest(); }

  public void testEnumConstInSwitchOutside() { doTest(); }

  public void testIntConstInSwitch() { doTest(); }

  public void testDoubleEmptyArray() {
    configureByTestName();
    checkResultByFile("/"+getTestName(false) + ".java");
    assertEquals(2, myItems.length);
  }

  public void testCollectionsEmptySetInMethodCall() { doTest(); }

  public void testCollectionsEmptySetInTernary() { doTest(); }

  public void testStringConstantInAnno() { doTest(); }

  public void testCollectionsEmptySetInTernary2() { doTest(); }

  public void testConstructorOnSeparateLineInMethodCall() { doTest(); }

  public void testConstructorWithExistingParens() { doTest(); }

  public void testMethodAnnotationNamedParameter() { doTest(); }
  
  public void testInheritedClass() { doTest(); }

  public void testClassLiteralInAnno1() { doTest(); }

  public void testMeaninglessExplicitWildcardParam() { doTest(); }

  public void testExplicitWildcardArrayParam() { doTest(); }

  public void testCatchInAnonymous() { doTest(); }

  public void testThrowRuntimeException() { doTest(); }

  public void testParameterizedConstructor() { doTest(); }

  public void testNewInnerClassNameShortPrefix() { doTest('\n'); }

  public void testNewInnerOfParameterizedClass() { doTest(); }

  public void testQualifiedThisInAnonymousConstructor() { doTest(); }

  public void testExceptionTwice() { doTest(); }

  public void testExceptionTwice2() { doTest(); }

  public void testNewInnerRunnable() { doTest(); }

  public void testArrayAccessIndex() { doTest(); }

  public void testThrowExceptionConstructor() { doTest('\n'); }

  public void testJavadocThrows() { doTest(); }

  public void testMethodThrows() { doTest(); }

  public void testDoNotExcludeAssignedVariable() { doTest(); }

  public void testArrayIndexTailType() { doTest(); }

  public void testPrivateOverloads() { doTest(); }

  public void testPolyadicExpression() { doTest(); }

  public void testCastAutoboxing() {
    doItemTest();
  }
  public void testCastAutoboxing2() {
    doItemTest();
  }
  public void testCastAutoboxing3() {
    doItemTest();
  }
  public void testCastWildcards() { doTest(); }

  public void testNoSecondMethodTypeArguments() { doTest(Lookup.REPLACE_SELECT_CHAR); }

  public void testNoFieldsInSuperConstructorCall() { doTest(); }

  public void testChainMethodsInSuperConstructorCall() { doTest(); }

  public void testNoUninitializedFieldsInConstructor() {
    configureByTestName();
    assertStringItems("aac", "aab", "hashCode");
  }
  public void testFieldsSetInAnotherConstructor() { doTest(); }
  public void testFieldsSetAbove() { doTest(); }

  public void testHonorSelection() {
    configureByTestName();
    select();
    checkResultByTestName();
  }

  public void testTypeParametersInheritors() {
    configureByTestName();
    assertStringItems("Foo", "Bar", "Goo");
    select();
    checkResultByTestName();
  }

  public void testVoidExpectedType() {
    configureByTestName();
    assertStringItems("notify", "notifyAll", "wait", "wait", "wait", "equals", "hashCode", "toString", "getClass");
    type("eq");
    assertEquals("equals", assertOneElement(getLookup().getItems()).getLookupString());
    select();
    checkResultByTestName();
  }

  public void testDoubleSemicolonPenetration() { doTest(); }

  public void testTypeParametersInheritorsComma() { doTest(); }

  public void testTypeParametersInheritorsInExpression() { doTest(); }

  //do we need to see all Object inheritors at all?
  public void _testTypeParametersObjectInheritors() { doTest(); }

  public void testDoubleThis() {
    doTest();
    assertNull(myItems);
  }

  public void testSmartFinish() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR); }

  public void testSillyAssignmentInTernary() { doTest(); }

  public void testSameFieldInAnotherObject() { doTest(); }

  public void testUnqualifiedConstantInSwitch() { doTest(); }

  public void testAmbiguousConstant() { doTest(); }

  public void testSameNamedFieldAndLocal() { doTest(); }

  public void testNoTailWhenNoPairBracket() { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  public void testNoTailWhenNoPairBracket2() { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  public void testAnonymousNoPairBracket() { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  private void doTestNoPairBracket(final char c) {
    boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;
    try {
      doTest(c);
    }
    finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = old;
    }
  }

  public void testNoConstructorTailWhenNoPairBracket() { doTestNoPairBracket(Lookup.NORMAL_SELECT_CHAR); }

  public void testConstructorNoPairBracketSemicolon() { doTestNoPairBracket(';'); }

  public void testMethodNoPairBracketComma() { doTestNoPairBracket(','); }

  public void testAbstractClassTwice() {
    configureByTestName();
    assertOneElement(myItems);
  }

  public void testConstantTwice() { doTest(); }

  public void testConstantTwice2() {
    configureByTestName();
    assertEquals(2, myItems.length);
  }

  public void testNoKeyConstant() {
    configureByTestName();
    assertStringItems("A_KEY", "create");
  }

  public void testUserDataListAddAll() {
    doTest();
  }

  public void testStaticSubclass() {
    doTest();
  }

  public void testMethodCallDot() { doTest('\n'); }
  public void testNegateVariable() { doTest(); }

  public void testExclamationMethodFinish() { doTest('!'); }
  public void testExclamationVariableFinish() { doTest('!'); }
  public void testExclamationStaticFieldFinish() { doTest('!'); }
  public void testExclamationFinishNonBoolean() { doTest('!'); }

  public void testExcludeDeclaredConstant() { doTest(); }

  public void testTabMethodInBinaryExpression() { doTest('\t'); }

  public void testIfConditionBinaryExpression() { doTest(); }

  public void testDelegationToParent() { doTest('\t'); }

  public void testBeforeBinaryExpressionInMethodCall() { doTest(); }

  public void testAssignableToAfterCast() { doTest(); }

  public void testInstanceMethodParametersFromStaticContext() { doTest(); }

  public void testInstanceMethodParametersFromStaticContext2() { doTest(); }

  public void testBeforeCastToArray() { doTest(); }

  public void testHidingFields() { doTest(); }

  public void testVoidCast() { doAntiTest(); }

  public void testIntPlusLongNotDouble() { doTest(); }

  public void testNestedAssignments() { doTest(); }

  public void testAfterNewInTernary() { doTest(); }

  public void testSuggestAnythingWhenWildcardExpected() {
    configureByTestName();
    assertStringItems("X", "Y", "Z");
  }

  public void testNewVararg() {
    configureByTestName();
    assertStringItems("Foo", "Foo", "Foo");
    assertEquals("{...} (default package)", LookupElementPresentation.renderElement(myItems[0]).getTailText());
    assertEquals("[] (default package)", LookupElementPresentation.renderElement(myItems[1]).getTailText());
    assertEquals("[]{...} (default package)", LookupElementPresentation.renderElement(myItems[2]).getTailText());
  }

  public void testNewVararg2() {
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

  public void testInsideStringLiteral() { doAntiTest(); }

  public void testDefaultAnnoParam() { doTest(); }

  public void testNewWithTypeParameterErasure() { doTest(); }

  public void testEverythingDoubles() {
    configureByTestName();
    assertStringItems("hashCode", "indexOf", "lastIndexOf", "size");
  }

  public void testNonStaticInnerClass() {
    configureByTestName();
    assertEmpty(myItems);
    checkResultByFile("/" + getTestName(false) + ".java");
  }

  //todo 2nd completion
  public void _testDefaultAnnoParam2() { doTest(); }
  
  public void testAnnotationValue() {doTest(); }

  public void testLiveTemplate() {
    final Template template = TemplateManager.getInstance(getProject()).createTemplate("foo", "zzz");
    template.addTextSegment("FooFactory.createFoo()");
    final SmartCompletionContextType completionContextType =
      ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), SmartCompletionContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(completionContextType, true);
    CodeInsightTestUtil.addTemplate(template, myFixture.getTestRootDisposable());
    doTest();
  }

  public void testInThisExpression() { doTest(); }

  public void testSuggestNull() { doTest(); }

  public void testNoNullAfterDot() {
    configureByTestName();
    assertEmpty(myItems);
    checkResultByFile("/" + getTestName(false) + ".java");
  }

  public void testDefaultAnnoMethodValue() { doTest(); }

  public void testNewAnonymousFunction() { doTest(); }

  public void testNewRunnableInsideMethod() {
    CommonCodeStyleSettings settings = getCodeStyleSettings();
    boolean lParenOnNextLine = settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE;
    try {
      settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
      doTest();
    } finally {
      settings.CALL_PARAMETERS_LPAREN_ON_NEXT_LINE = lParenOnNextLine;
    }
  }

  public void testNewRunnableInsideMethodMultiParams() {
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

  public void testUseIntConstantsFromTargetClass() { doTest(); }
  public void testUseObjectConstantsFromTargetClass() { doTest(); }
  public void testUseIntConstantsFromTargetClassReturnValue() { doTest(); }
  public void testUseIntConstantsFromConstructedClass() { doTest(); }
  public void testUseIntConstantsInPlus() { doTest(); }
  public void testUseIntConstantsInOr() { doTest(); }

  public void testExtraSemicolonAfterMethodParam() {
    getCodeStyleSettings().SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    doTest();
  }

  public void testNoSemicolonInsideParentheses() { doTest(); }

  public void testAssignFromTheSameFieldOfAnotherObject() {
    doTest();
  }

  public void testTailAfterInstanceOf() {
    doTest();
  }

  public void testReplaceWholeReferenceChain() { doTest(Lookup.REPLACE_SELECT_CHAR); }

  public void testDoubleTrueInOverloadedMethodCall() { doTest(Lookup.REPLACE_SELECT_CHAR); }

  public void testMethodColon() { doFirstItemTest(':'); }
  public void testVariableColon() { doFirstItemTest(':'); }
  public void testConditionalColonOnNextLine() { doFirstItemTest(':'); }

  private void doFirstItemTest(char c) {
    configureByTestName();
    select(c);
    checkResultByTestName();
  }

  public void testOneElementArray() { doTest(); }

  public void testCastToArray() { doTest(); }

  public void testCommaDoublePenetration() {
    doFirstItemTest(',');
  }

  public void testSuperMethodArguments() {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(1));
    select();
    checkResultByTestName();
  }

  public void testDelegateMethodArguments() {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(1));
    select();
    checkResultByTestName();
  }

  public void testSameMethodArgumentsInIf() {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(1));
    select();
    checkResultByTestName();
  }

  public void testSuperConstructorArguments() {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    select();
    checkResultByTestName();
  }

  public void testSameNamedArguments() {
    configureByTestName();
    getLookup().setCurrentItem(getLookup().getItems().get(4));
    select();
    checkResultByTestName();
  }

  public void testSameNamedArgumentsDelegation() {
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

  public void testWrongAnonymous() {
    configureByTestName();
    select();
    checkResultByTestName();
  }

  public void testAfterNewWithGenerics() {
    doActionTest();
  }

  public void testClassLiteral() {
    doActionTest();
    assertStringItems("String.class");

    LookupElementPresentation p = new LookupElementPresentation();
    myFixture.getLookupElements()[0].renderElement(p);
    assertEquals("String.class", p.getItemText());
    assertEquals(" (java.lang)", p.getTailText());
    assertNull(p.getTypeText());
  }
  public void testNoClassLiteral() {
    doActionTest();
    assertStringItems("Object.class", "getClass", "forName", "forName");
  }

  public void testClassLiteralInAnno2() {
    doItemTest();
  }

  public void testClassLiteralInheritors() {
    doItemTest();
  }

  public void testInsertOverride() throws Exception {
    JavaCodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject()).getCustomSettings(JavaCodeStyleSettings.class);
    styleSettings.INSERT_OVERRIDE_ANNOTATION = true;
    doItemTest();
  }

  public void testForeach() {
    doActionTest();
  }

  public void testIDEADEV2626() {
    doItemTest();
  }

  public void testDontSuggestWildcardGenerics() { doItemTest(); }

  public void testCastWith2TypeParameters() { doTest(); }
  public void testClassLiteralInArrayAnnoInitializer() { doTest(); }
  public void testClassLiteralInArrayAnnoInitializer2() { doTest(); }

  public void testAnnotation() {
    configureByTestName();
    assertStringItems("ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR",
                      "ElementType.FIELD", "ElementType.LOCAL_VARIABLE",
                      "ElementType.METHOD", "ElementType.PACKAGE", "ElementType.PARAMETER",
                      "ElementType.TYPE" /*, "ElementType.TYPE_PARAMETER", "ElementType.TYPE_USE"*/);
  }

  public void testAnnotation2() {
    configureByTestName();
    assertStringItems("RetentionPolicy.CLASS", "RetentionPolicy.RUNTIME", "RetentionPolicy.SOURCE");
  }
  public void testAnnotation2_2() {
    configureByTestName();
    assertSameElements(myFixture.getLookupElementStrings(), "RetentionPolicy.CLASS", "RetentionPolicy.SOURCE", "RetentionPolicy.RUNTIME");
  }

  public void testAnnotation3() {
    doTest();
  }

  public void testAnnotation3_2() {
    doTest();
  }

  public void testAnnotation4() {
    configureByTestName();
    checkResultByTestName();

    assertStringItems("false", "true");
  }

  public void testAnnotation5() {
    configureByTestName();
    checkResultByTestName();

    assertStringItems("CONNECTION", "NO_CONNECTION");
  }

  public void testAnnotation6() {
    configureByTestName();
  
    assertStringItems("ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR",
                      "ElementType.FIELD", "ElementType.LOCAL_VARIABLE",
                      "ElementType.METHOD", "ElementType.PACKAGE", "ElementType.PARAMETER",
                      "ElementType.TYPE"/*, "ElementType.TYPE_PARAMETER", "ElementType.TYPE_USE"*/);
  }

  public void testArrayClone() {
    doTest();
  }

  public void testIDEADEV5150() {
    doTest('\n');
  }

  public void testIDEADEV7835() {
    doTest();
  }

  public void testTypeArgs1() {
    doTest();
  }

  public void testTypeArgs2() {
    doTest();
  }
  public void testTypeArgsOverwrite() { doTest(); }

  public void testIfConditionExpectedType() { doTest(); }

  public void testUnboundTypeArgs() { doTest(); }
  public void testUnboundTypeArgs2() { doTest(); }
  public void testSameTypeArg() { doTest(); }

  public void testIDEADEV2668() {
    doTest();
  }

  public void testExcessiveTail() { doTest(); }
  public void testSeveralTypeArguments() { doTest(); }
  public void testSeveralTypeArgumentsSomeUnknown() { doTest(); }

  public void testExtendsInTypeCast() {
    doTest();
  }

  public void testTabMethodCall() {
    doFirstItemTest(Lookup.REPLACE_SELECT_CHAR);
  }

  public void testConstructorArgsSmartEnter() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR); }

  public void testIDEADEV13148() {
    configureByFile("/IDEADEV13148.java");
    assertStringItems("false", "true"); //todo don't suggest boolean literals in synchronized
  }

  public void testSuggestNames() {
    configureByTestName();
    assertStringItems("arrayList", "list");
  }

  public void testOverloadedMethods() {
    doTest();
  }

  public void testNoCommaBeforeVarargs() { doTest(); }

  public void testEnumField() {
    doItemTest();
  }

  public void testEnumField1() {
    configureByTestName();
    checkResultByTestName();
    assertEquals(4, myItems.length);
  }

  public void testInsertTypeParametersOnImporting() { doTest('\n'); }

  public void testEmptyListInReturn() { doItemTest(); }

  public void testEmptyListInReturn2() { doTest(); }

  public void testEmptyListInReturnTernary() { doItemTest(); }

  public void testEmptyListBeforeSemicolon() { doItemTest(); }

  public void testEmptyListWithCollectionsPrefix() { doItemTest(); }

  public void testForeachLoopVariableInIterableExpression() { doAntiTest(); }

  public void testStaticallyImportedMagicMethod() {
    configureByTestName();
    assertStringItems("foo");
    selectItem(myItems[0], '\t');
    checkResultByTestName();
  }

  public void _testCallVarargArgument() { doTest(); }

  public void testTabToReplaceClassKeyword() {
    configureByTestName();
    selectItem(myItems[0], Lookup.REPLACE_SELECT_CHAR);
    checkResultByTestName();
  }

  public void testNoTypeParametersForToArray() {
    doTest();
  }

  public void testStaticallyImportedField() { doTest('\n'); }
  public void testSiblingOfAStaticallyImportedField() { doTest(); }
  public void testPrimitiveArrayClassInMethod() { doTest(); }
  public void testPrimitiveClassInAnno() { doTest(); }
  public void testNewInnerClassOfSuper() { doTest(); }
  public void testAssertThatMatcher() { doTest(); }

  public void testInferFromCall() {
    doTest();
  }

  public void testInferFromCall1() {
    doTest();
  }

  public void testCastToParameterizedType() { doActionTest(); }

  public void testInnerEnumInMethod() {
    doItemTest();
  }

  public void testEnumAsDefaultAnnotationParam() { doTest(); }
  public void testBreakLabel() { doTest(); }

  public void testNewAbstractInsideAnonymous() { doTest(); }

  public void testFilterPrivateConstructors() { doTest(); }

  public void testExplicitMethodTypeParametersQualify() { doTest(); }
  public void testExplicitMethodTypeParametersOverZealous() { doTest(); }
  public void testExplicitMethodTypeParametersFromSuperClass() { doTest(); }

  public void testWildcardedInstanceof() { doTest(); }
  public void testWildcardedInstanceof2() { doTest(); }
  public void testWildcardedInstanceof3() { doTest(); }

  public void testCheckStaticImportsType() { doAntiTest(); }
  public void testThisFieldAssignedToItself() { doAntiTest(); }

  public void testCaseMissingEnumValue() { doTest(); }
  public void testCaseMissingEnumValue2() { doTest(); }
  
  public void testNoHiddenParameter() { doTest(); }

  public void testTypeVariableInstanceOf() {
    configureByTestName();
    performAction();
    assertStringItems("Bar", "Goo");
  }

  public void testAutoImportExpectedType() {
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

  public void testNoWrongSubstitutorFromStats() {
    doTest();
    FileDocumentManager.getInstance().saveDocument(myFixture.getEditor().getDocument());
    doTest(); // stats are changed now
  }

  public void testCommonPrefixWithSelection() {
    doItemTest();
  }

  public void testNewAbstractClassWithConstructorArgs() {
    doItemTest();
  }

  public void testArrayInitializerBeforeVarargs() { doTest(); }
  public void testDuplicateMembersFromSuperClass() { doTest(); }
  public void testInnerAfterNew() { doTest(); }
  public void testOuterAfterNew() { doTest(); }
  public void testEverythingInStringConcatenation() { doTest(); }
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

  public void testNoNewEnum() {
    configureByTestName();
    assertStringItems("Foo");
  }

  public void testDuplicateMembersFromSuperClassInAnotherFile() {
    myFixture.addClass("class Super { public static final Super FOO = null; }");
    doTest();
  }

  public void testInsideGenericClassLiteral() {
    configureByTestName();
    assertStringItems("String.class", "StringBuffer.class", "StringBuilder.class");
  }

  public void testArrayAnnoParameter() {
    doActionTest();
  }

  public void testInnerClassImports() {
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

  public void testCastWithGenerics() {
    doActionTest();
  }

  public void testInnerEnum() {
    configureByTestName();

    getLookup().setCurrentItem(ContainerUtil.find(myItems, lookupItem -> "Bar.Fubar.Bar".equals(lookupItem.getLookupString())));
    select('\n');
    checkResultByTestName();
  }

  public void testQualifiedAfterNew() {
    myFixture.addClass("package foo; public interface Foo<T> {}");
    myFixture.addClass("package bar; public class Bar implements foo.Foo {}");
    doTest();
  }
  public void testAfterQualifiedNew() {
    myFixture.addClass("class Aa { public class B { } }");
    doTest();
  }

  public void testTabAfterNew() {
    doFirstItemTest('\t');
  }

  public void testSuggestMethodReturnType() { 
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "Serializable", "CharSequence", "Object");
  }

  public void testSuggestCastReturnTypeByCalledMethod() { doTest(); }

  public void testOnlyInterfacesInImplements() { doTest(); }

  public void testNonStaticField() { doAntiTest(); }

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
