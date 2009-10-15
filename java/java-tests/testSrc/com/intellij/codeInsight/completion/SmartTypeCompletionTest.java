package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.TestLookupManager;
import com.intellij.codeInsight.template.SmartCompletionContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;

public class SmartTypeCompletionTest extends LightCompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/smartType";
  private LanguageLevel myPrevLanguageLevel;

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testParenAfterCast1() throws Exception {
    String path = BASE_PATH + "/parenAfterCast";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testParenAfterCast2() throws Exception {
    String path = BASE_PATH + "/parenAfterCast";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");


  }


  public void testParenAfterCast3() throws Exception {
    String path = BASE_PATH + "/parenAfterCast";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testParenAfterCall1() throws Exception {
    String path = BASE_PATH + "/parenAfterCall";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testParenAfterCall2() throws Exception {
    String path = BASE_PATH + "/parenAfterCall";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testParenAfterCall3() throws Exception {
    String path = BASE_PATH + "/parenAfterCall";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testParenAfterCall4() throws Exception {
    String path = BASE_PATH + "/parenAfterCall";

    configureByFile(path + "/before4.java");
    checkResultByFile(path + "/after4.java");
  }

  public void testParenAfterCall5() throws Exception {
    String path = BASE_PATH + "/parenAfterCall";

    configureByFile(path + "/before5.java");
    checkResultByFile(path + "/after5.java");
  }

  public void testParenAfterCall6() throws Exception {
    String path = BASE_PATH + "/parenAfterCall";

    configureByFile(path + "/before6.java");
    checkResultByFile(path + "/after6.java");
  }
  
  public void testParenAfterCall1_SpaceWithinMethodCallParens() throws Exception {
    String path = BASE_PATH + "/parenAfterCall";

    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject());
    configureByFileNoComplete(path + "/before1.java");
    styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    try{
      complete();
    }
    finally{
      styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    }
    checkResultByFile(path + "/after1_space.java");
  }

  public void testParenAfterIf1() throws Exception {
    String path = BASE_PATH + "/parenAfterIf";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testParenAfterIf2() throws Exception {
    String path = BASE_PATH + "/parenAfterIf";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testForceLookupForAbstractClasses() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before9.java");
    checkResultByFile(path + "/after9.java");
  }

  public void testAfterNew1() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before1.java");
    select();
    checkResultByFile(path + "/after1.java");
  }

  public void testAfterNew2() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before2.java");
    select();
    checkResultByFile(path + "/after2.java");
  }

  public void testAfterNew3() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before3.java");
    select();
    checkResultByFile(path + "/after3.java");
  }

  public void testAfterNew4() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before4.java");
    select();
    checkResultByFile(path + "/after4.java");
  }

  public void testAfterNew5() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before5.java");
    select();
    checkResultByFile(path + "/after5.java");
  }

  public void testAfterNew6() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before6.java");
    select();
    checkResultByFile(path + "/after6.java");
  }

  public void testAfterNew7() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before7.java");
    select();
    checkResultByFile(path + "/after7.java");
  }

  public void testAfterNew8() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before8.java");
    select();
    checkResultByFile(path + "/after8.java");
  }

  public void testAfterNew9() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before10.java");
    select();
    checkResultByFile(path + "/after10.java");
  }

  public void testAfterNew10() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before12.java");
    //select();
    checkResultByFile(path + "/after12.java");
  }

  public void testAfterNew11() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before13.java");
    //select();
    checkResultByFile(path + "/after13.java");
  }

  public void testAfterThrowNew1() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before14.java");
    //select();
    checkResultByFile(path + "/after14.java");
  }

  public void testAfterThrowNew2() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before15.java");
    //select();
    checkResultByFile(path + "/after15.java");
  }

  public void testAfterThrowNew3() throws Exception {
    String path = BASE_PATH + "/afterNew";

    configureByFile(path + "/before16.java");
    //select();
    checkResultByFile(path + "/after16.java");
  }

  public void testAfterThrow1() throws Exception {
    String path = BASE_PATH;

    configureByFile(path + "/CastInThrow.java");
    //select();
    checkResultByFile(path + "/CastInThrow-out.java");
  }

  public void testParenAfterNewWithinInnerExpr() throws Exception {
    String path = BASE_PATH + "/afterNew";

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
    String path = BASE_PATH + "/return";
    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testReturn2() throws Exception{
    String path = BASE_PATH + "/return";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testReturn3() throws Exception{
    String path = BASE_PATH + "/return";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testGenerics1() throws Exception {
    String path = BASE_PATH + "/generics";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testGenerics2() throws Exception {
    String path = BASE_PATH + "/generics";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testGenerics3() throws Exception {
    String path = BASE_PATH + "/generics";

    configureByFile(path + "/before3.java");
    checkResultByFile(path + "/after3.java");
  }

  public void testGenerics4() throws Exception {
    String path = BASE_PATH + "/generics";

    configureByFile(path + "/before4.java");
    checkResultByFile(path + "/after4.java");
  }

  public void testGenerics5() throws Exception {
    String path = BASE_PATH + "/generics";

    configureByFile(path + "/before5.java");
    checkResultByFile(path + "/after5.java");
  }

  public void testAfterInstanceOf1() throws Exception {
    String path = BASE_PATH + "/afterInstanceOf";

    configureByFile(path + "/before1.java");
    checkResultByFile(path + "/after1.java");
  }

  public void testAfterInstanceOf2() throws Exception {
    String path = BASE_PATH + "/afterInstanceOf";

    configureByFile(path + "/before2.java");
    checkResultByFile(path + "/after2.java");
  }

  public void testInsideCatch() throws Exception {
    String path = BASE_PATH;

    configureByFile(path + "/InsideCatch.java");
    checkResultByFile(path + "/InsideCatch-out.java");
  }


  public void testGenerics6() throws Exception {
    String path = BASE_PATH + "/generics";

    configureByFile(path + "/before6.java");
    checkResultByFile(path + "/after6.java");
  }

  public void testWildcardNew1() throws Exception {
    String path = BASE_PATH + "/generics";

    configureByFile(path + "/before7.java");
    checkResultByFile(path + "/after7.java");
  }

  public void testWildcardNew2() throws Exception {
    String path = BASE_PATH + "/generics";

    configureByFile(path + "/before8.java");
    checkResultByFile(path + "/after8.java");
  }

  public void testWildcardEliminated() throws Exception {
    String path = BASE_PATH + "/generics";

    configureByFile(path + "/before9.java");
    checkResultByFile(path + "/after9.java");
  }

  public void testBug1() throws Exception { doTest(); }

  public void testQualifiedThis() throws Exception { doTest(); }

  public void testBug2() throws Exception {
    configureByFile(BASE_PATH + "/Bug2.java");
  }


  public void testSillyAssignment1() throws Exception {
    configureByFile(BASE_PATH + "/Silly1.java");
    checkResultByFile(BASE_PATH + "/Silly1.java");
  }

  public void testVarargs1() throws Exception { doTest(); }

  public void testEnumConstInSwitch() throws Exception { doTest(); }

  public void testEnumConstInSwitchOutside() throws Exception { doTest(); }

  public void testIntConstInSwitch() throws Exception { doTest(); }

  public void testDoubleEmptyArray() throws Exception {
    configureByFile(BASE_PATH + "/"+getTestName(false)+".java");
    checkResultByFile(BASE_PATH + "/"+getTestName(false) + ".java");
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

  public void testExplicitWildcardParam() throws Throwable { doTest(); }
  
  public void testExplicitWildcardArrayParam() throws Throwable { doTest(); }

  public void testCatchInAnonymous() throws Throwable { doTest(); }

  public void testThrowRuntimeException() throws Throwable { doTest(); }

  public void testParameterizedConstructor() throws Throwable { doTest(); }

  public void testNewInnerOfParameterizedClass() throws Throwable { doTest(); }
  
  public void testQualifiedThisInAnonymousConstructor() throws Throwable { doTest(); }

  public void testExceptionTwice() throws Throwable { doTest(); }

  public void testExceptionTwice2() throws Throwable { doTest(); }

  public void testNewInnerRunnable() throws Throwable { doTest(); }

  public void testArrayAccessIndex() throws Throwable { doTest(); }

  public void testThrowExceptionConstructor() throws Throwable { doTest(); }

  public void testJavadocThrows() throws Throwable { doTest(); }

  public void testDoNotExcludeAssignedVariable() throws Throwable { doTest(); }

  public void testArrayIndexTailType() throws Throwable { doTest(); }

  public void testHonorSelection() throws Throwable { doTest(); }

  public void testTypeParametersInheritors() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("Foo", "Bar", "Goo");
    select();
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testVoidExpectedType() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("notify", "notifyAll", "wait", "wait", "wait", "equals", "getClass", "hashCode", "toString");
    type('e');
    select();                         
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
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

  public void testSmartFinish() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    select(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testSillyAssignmentInTernary() throws Throwable { doTest(); }

  public void testSameFieldInAnotherObject() throws Throwable { doTest(); }

  public void testUnqualifiedConstantInSwitch() throws Throwable { doTest(); }

  public void testAmbiguousConstant() throws Throwable { doTest(); }

  public void testSameNamedFieldAndLocal() throws Throwable { doTest(); }

  public void testAbstractClassTwice() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    final int offset = myEditor.getCaretModel().getOffset();
    select();
    myEditor.getCaretModel().moveToOffset(offset);
    assertOneElement(myItems);
  }

  public void testConstantTwice() throws Throwable { doTest(); }

  public void testConstantTwice2() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertEquals(2, myItems.length);
  }

  public void testNoKeyConstant() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertEquals(2, myItems.length);
    assertEquals("A_KEY", myItems[0].getLookupString());
    assertEquals("Key.create", myItems[1].getLookupString());
  }

  public void testUserDataListAddAll() throws Throwable {
    doTest();
  }

  public void testStaticSubclass() throws Throwable {
    doTest();
  }

  public void testMethodCallDot() throws Throwable { doTest(); }
  public void testNegateVariable() throws Throwable { doTest(); }

  public void testExclamationMethodFinish() throws Throwable { doTest('!'); }
  public void testExclamationVariableFinish() throws Throwable { doTest('!'); }
  public void testExclamationStaticFieldFinish() throws Throwable { doTest('!'); }
  public void testExclamationFinishNonBoolean() throws Throwable { doTest('!'); }

  public void testExcludeDeclaredConstant() throws Throwable { doTest(); }

  public void testTabMethodInBinaryExpression() throws Throwable { doTest('\t'); }

  public void testIfConditionBinaryExpression() throws Throwable { doTest(); }

  public void testDelegationToParent() throws Throwable { doTest(); }

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
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("X<java.lang.Object>", "Y", "Z<java.lang.Object>"); 
  }

  public void testNewVararg() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("Foo", "Foo");
    assertEquals(0, myItems[0].as(PsiTypeLookupItem.class).getBracketsCount());
    assertEquals(1, myItems[1].as(PsiTypeLookupItem.class).getBracketsCount());
  }

  public void testInsideStringLiteral() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertNull(myItems);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");

  }

  public void testDefaultAnnoParam() throws Throwable { doTest(); }

  public void testCastGenericQualifier() throws Throwable { doTest(); }

  public void testEverythingDoubles() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertStringItems("hashCode", "indexOf", "lastIndexOf", "size");
  }

  public void testNonStaticInnerClass() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertNull(myItems);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");
  }

  //todo 2nd completion
  public void _testDefaultAnnoParam2() throws Throwable { doTest(); }

  public void testLiveTemplate() throws Throwable {
    final Template template = TemplateManager.getInstance(getProject()).createTemplate("foo", "zzz");
    template.addTextSegment("FooFactory.createFoo()");
    final SmartCompletionContextType completionContextType =
      ContainerUtil.findInstance(TemplateContextType.EP_NAME.getExtensions(), SmartCompletionContextType.class);
    ((TemplateImpl)template).getTemplateContext().setEnabled(completionContextType, true);
    TemplateSettings.getInstance().addTemplate(template);
    try {
      doTest();
    }
    finally {
      TemplateSettings.getInstance().removeTemplate(template);
    }
  }

  public void testInThisExpression() throws Throwable { doTest(); }

  public void testSuggestNull() throws Throwable { doTest(); }

  public void testNoNullAfterDot() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertNull(myItems);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");
  }

  public void testDefaultAnnoMethodValue() throws Throwable { doTest(); }

  public void testUseIntConstantsFromTargetClass() throws Throwable { doTest(); }
  public void testUseIntConstantsFromTargetClassReturnValue() throws Throwable { doTest(); }
  public void testUseIntConstantsFromConstructedClass() throws Throwable { doTest(); }

  public void testExtraSemicolonAfterMethodParam() throws Throwable {
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject());
    styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    try{
      doTest();
    }
    finally{
      styleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    }
  }

  public void testAssignFromTheSameFieldOfAnotherObject() throws Throwable {
    doTest();
  }

  public void testTailAfterInstanceOf() throws Throwable {
    doTest();
  }

  public void testSuggestInstanceofedValue() throws Throwable {
    doTest();
  }

  public void testSuggestInstanceofedValueInTernary() throws Throwable {
    doTest();
  }

  public void testSuggestInstanceofedValueInComplexIf() throws Throwable { doTest(); }

  public void testSuggestInstanceofedValueInElseNegated() throws Throwable { doTest(); }
  
  public void testSuggestInstanceofedValueAfterReturn() throws Throwable { doTest(); }

  public void testNoInstanceofedValueWhenBasicSuits() throws Throwable { doTest(); }

  public void testSuggestCastedValueAfterCast() throws Throwable { doTest(); }

  public void testNoInstanceofedValueInElse() throws Throwable { doAntiTest(); }

  public void testNoInstanceofedValueInThenNegated() throws Throwable { doAntiTest(); }

  public void testNoInstanceofedValueInElseWithComplexIf() throws Throwable { doAntiTest(); }

  public void testReplaceWholeReferenceChain() throws Throwable { doTest(Lookup.REPLACE_SELECT_CHAR); }

  public void testInstanceofedInsideAnonymous() throws Throwable { doTest(Lookup.REPLACE_SELECT_CHAR); }

  public void testDoubleTrueInOverloadedMethodCall() throws Throwable { doTest(Lookup.REPLACE_SELECT_CHAR); }

  public void testOneElementArray() throws Throwable { doTest(); }

  public void testCastToArray() throws Throwable { doTest(); }

  public void testDontAutoCastWhenAlreadyCasted() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertEquals("s", myItems[0].getLookupString());
    assertEquals("String.copyValueOf", myItems[1].getLookupString());
    select();
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testAutoCastWhenAlreadyCasted() throws Throwable { doTest(); }

  public void testCommaDoublePenetration() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    select(',');
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testSuperMethodArguments() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    getLookup().setCurrentItem(getLookup().getItems().get(1));
    select();
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testDelegateMethodArguments() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    getLookup().setCurrentItem(getLookup().getItems().get(1));
    select();
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  public void testSuperConstructorArguments() throws Throwable {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    select();
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  private void doAntiTest() throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    assertNull(myItems);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");
  }

  private void doTest() throws Exception {
    doTest(Lookup.NORMAL_SELECT_CHAR);
  }

  private void doTest(final char c) throws Exception {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION;
    if (c != Lookup.NORMAL_SELECT_CHAR) {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = false;
    }

    try {
      configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
      if (myItems != null && myItems.length == 1) {
        select(c);
      }
      checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
    }
    finally {
      if (c != Lookup.NORMAL_SELECT_CHAR) {
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_SMART_TYPE_COMPLETION = old;
      }
    }

  }

  protected void checkResultByFile(@NonNls final String filePath) throws Exception {
    if (myItems != null) {
      System.out.println("items = " + Arrays.asList(myItems));
    }
    super.checkResultByFile(filePath);
  }

  public void testInnerEnum() throws Exception {
    configureByFile(BASE_PATH + "/"+getTestName(false)+".java");

    LookupManager.getActiveLookup(myEditor).setCurrentItem(ContainerUtil.find(myItems, new Condition<LookupElement>() {
      public boolean value(final LookupElement lookupItem) {
        return "Fubar.Bar".equals(lookupItem.getLookupString());
      }
    }));
    select('\n');
    checkResultByFile(BASE_PATH + "/"+getTestName(false) + "-out.java");
  }

  protected void setUp() throws Exception {
    super.setUp();
    myPrevLanguageLevel = LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    setType(CompletionType.SMART);
  }

  protected void tearDown() throws Exception {
    LookupManager.getInstance(getProject()).hideActiveLookup();
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(myPrevLanguageLevel);
    super.tearDown();
  }

  private void select() {
    select(Lookup.NORMAL_SELECT_CHAR);
  }

  private void select(final char c) {
    if (c != '\n' && c != '\t' && c != Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
      type(c);
      return;
    }

    final TestLookupManager manager = (TestLookupManager) LookupManager.getInstance(getProject());
    final Lookup lookup = manager.getActiveLookup();
    if(lookup != null) {
      manager.forceSelection(c, lookup.getCurrentItem());
    }
  }
}
