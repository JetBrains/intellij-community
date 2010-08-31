package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.lookup.impl.TestLookupManager;
import com.intellij.codeInsight.template.SmartCompletionContextType;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

public class SmartTypeCompletionTest extends LightCompletionTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/smartType";

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
    configureByTestName();
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
    assertStringItems("notify", "notifyAll", "wait", "wait", "wait", "equals", "getClass", "hashCode", "toString");
    type('e');
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

  public void testSmartFinish() throws Throwable {
    configureByTestName();
    select(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
    checkResultByTestName();
  }

  public void testSillyAssignmentInTernary() throws Throwable { doTest(); }

  public void testSameFieldInAnotherObject() throws Throwable { doTest(); }

  public void testUnqualifiedConstantInSwitch() throws Throwable { doTest(); }

  public void testAmbiguousConstant() throws Throwable { doTest(); }

  public void testSameNamedFieldAndLocal() throws Throwable { doTest(); }

  public void testAbstractClassTwice() throws Throwable {
    configureByTestName();
    final int offset = myEditor.getCaretModel().getOffset();
    select();
    myEditor.getCaretModel().moveToOffset(offset);
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
    configureByTestName();
    assertStringItems("X<java.lang.Object>", "Y", "Z<java.lang.Object>"); 
  }

  public void testNewVararg() throws Throwable {
    configureByTestName();
    assertStringItems("Foo", "Foo");
    assertEquals(0, myItems[0].as(PsiTypeLookupItem.class).getBracketsCount());
    assertEquals(1, myItems[1].as(PsiTypeLookupItem.class).getBracketsCount());
  }

  public void testInsideStringLiteral() throws Throwable {
    configureByTestName();
    assertNull(myItems);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");

  }

  public void testDefaultAnnoParam() throws Throwable { doTest(); }

  public void testCastGenericQualifier() throws Throwable { doTest(); }

  public void testEverythingDoubles() throws Throwable {
    configureByTestName();
    assertStringItems("hashCode", "indexOf", "lastIndexOf", "size");
  }

  public void testNonStaticInnerClass() throws Throwable {
    configureByTestName();
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
    configureByTestName();
    assertNull(myItems);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");
  }

  public void testDefaultAnnoMethodValue() throws Throwable { doTest(); }

  public void testNewAnonymousFunction() throws Throwable { doTest(); }

  public void testUseIntConstantsFromTargetClass() throws Throwable { doTest(); }
  public void testUseIntConstantsFromTargetClassReturnValue() throws Throwable { doTest(); }
  public void testUseIntConstantsFromConstructedClass() throws Throwable { doTest(); }
  public void testUseIntConstantsInPlus() throws Throwable { doTest(); }
  public void testUseIntConstantsInOr() throws Throwable { doTest(); }

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
    configureByTestName();
    assertEquals("s", myItems[0].getLookupString());
    assertEquals("copyValueOf", myItems[1].getLookupString());
    select();
    checkResultByTestName();
  }

  public void testAutoCastWhenAlreadyCasted() throws Throwable { doTest(); }

  public void testCommaDoublePenetration() throws Throwable {
    configureByTestName();
    select(',');
    checkResultByTestName();
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

  private void doAntiTest() throws Exception {
    configureByTestName();
    assertNull(myItems);
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + ".java");
  }

  public void testAfterNewWithGenerics() throws Exception {
    doActionTest();
  }

  public void testClassLiteral() throws Exception {
    doActionTest();
    assertStringItems("String.class");
  }
  public void testNoClassLiteral() throws Exception {
    doActionTest();
    assertStringItems("Object.class", "getClass", "forName", "forName");
  }

  public void testClassLiteralInAnno2() throws Throwable {
    doActionItemTest();
  }

  public void testClassLiteralInheritors() throws Throwable {
    doActionItemTest();
  }

  public void testAfterNew15() throws Exception {
    setLanguageLevel(LanguageLevel.JDK_1_5);
    doActionItemTest();
  }

  public void testInsertOverride() throws Exception {
    CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(getProject());
    styleSettings.INSERT_OVERRIDE_ANNOTATION = true;
    doActionItemTest();
  }

  public void testForeach() throws Exception {
    doActionTest();
  }

  public void testIDEADEV2626() throws Exception {
    doActionTest();
  }

  public void testCastWith2TypeParameters() throws Throwable { doTest(); }

  public void testAnnotation() throws Exception {
    doTest();
    testByCount(8, "ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR", "ElementType.FIELD", "ElementType.LOCAL_VARIABLE",
                "ElementType.METHOD", "ElementType.PACKAGE", "ElementType.PARAMETER", "ElementType.TYPE");
  }

  protected void checkResultByFile(@NonNls final String filePath) throws Exception {
    if (myItems != null) {
      //System.out.println("items = " + Arrays.asList(myItems));
    }
    super.checkResultByFile(filePath);
  }

  public void testAnnotation2() throws Exception {
    doTest();
    testByCount(3, "RetentionPolicy.CLASS", "RetentionPolicy.SOURCE", "RetentionPolicy.RUNTIME");
  }
  public void testAnnotation2_2() throws Exception {
    doTest();
    testByCount(3, "RetentionPolicy.CLASS", "RetentionPolicy.SOURCE", "RetentionPolicy.RUNTIME");
  }

  public void testAnnotation3() throws Exception {
    doTest();
  }

  public void testAnnotation3_2() throws Exception {
    doTest();
  }

  public void testAnnotation4() throws Exception {
    doTest();

    testByCount(2, "true","false");
  }

  public void testAnnotation5() throws Exception {
    doTest();

    testByCount(2, "CONNECTION","NO_CONNECTION");
  }

  public void testAnnotation6() throws Exception {
    doTest();

    testByCount(8, "ElementType.ANNOTATION_TYPE", "ElementType.CONSTRUCTOR", "ElementType.FIELD", "ElementType.LOCAL_VARIABLE",
                "ElementType.METHOD", "ElementType.PACKAGE", "ElementType.PARAMETER", "ElementType.TYPE");
  }

  public void testArrayClone() throws Exception {
    doTest();
  }

  public void testIDEADEV5150() throws Exception {
    doTest();
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

  public void testIDEADEV2668() throws Exception {
    doTest();
  }

  public void testExcessiveTail() throws Exception {
    doTest();
  }

  public void testExtendsInTypeCast() throws Exception {
    doTest();
  }

  public void testTabMethodCall() throws Exception {
    configureByTestName();
    select(Lookup.REPLACE_SELECT_CHAR);
    checkResultByTestName();
  }

  public void testConstructorArgsSmartEnter() throws Exception {
    configureByTestName();
    select(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
    checkResultByTestName();
  }

  private void configureByTestName() throws Exception {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
  }

  public void testIDEADEV13148() throws Exception {
    configureByFile(BASE_PATH + "/IDEADEV13148.java");
    testByCount(0);
  }

  public void testOverloadedMethods() throws Throwable {
    doTest();
  }

  public void testEnumField() throws Throwable {
    doItemTest();
  }

  public void testEnumField1() throws Exception {
    doTest();
    assertEquals(4, myItems.length);
  }

  public void testInsertTypeParametersOnImporting() throws Throwable {
    doTest();
  }

  public void testEmptyListInReturn() throws Throwable {
    doItemTest();
  }

  public void testEmptyListInReturn2() throws Throwable {
    doTest();
  }

  public void testEmptyListInReturnTernary() throws Throwable {
    doItemTest();
  }

  public void testEmptyListBeforeSemicolon() throws Throwable {
    doItemTest();
  }

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

  public void testInferFromCall() throws Throwable {
    doTest();
  }

  public void testInferFromCall1() throws Throwable {
    doTest();
  }

  public void testCastToParameterizedType() throws Throwable { doActionTest(); }

  public void testInnerEnumInMethod() throws Throwable {
    doActionItemTest();
  }

  public void testEnumAsDefaultAnnotationParam() throws Throwable { doTest(); }

  public void testNewAbstractInsideAnonymous() throws Throwable { doTest(); }

  public void testFilterPrivateConstructors() throws Throwable { doTest(); }

  public void testExplicitMethodTypeParametersQualify() throws Throwable { doTest(); }

  public void testWildcardedInstanceof() throws Throwable { doTest(); }
  public void testWildcardedInstanceof2() throws Throwable { doTest(); }
  public void testWildcardedInstanceof3() throws Throwable { doTest(); }

  public void testTypeVariableInstanceOf() throws Throwable {
    configureByTestName();
    performAction();
    assertStringItems("Bar", "Goo");
  }

  public void testCommonPrefixWithSelection() throws Throwable {
    doItemTest();
  }

  public void testNewAbstractClassWithConstructorArgs() throws Throwable {
    doItemTest();
  }

  public void testInsideGenericClassLiteral() throws Throwable {
    configureByTestName();
    testByCount(3, "String.class", "StringBuffer.class", "StringBuilder.class");
  }

  public void testArrayAnnoParameter() throws Throwable {
    doActionTest();
  }

  public void testCastWithGenerics() throws Throwable {
    doActionTest();
  }

  public void testInnerEnum() throws Exception {
    configureByTestName();

    getLookup().setCurrentItem(ContainerUtil.find(myItems, new Condition<LookupElement>() {
      public boolean value(final LookupElement lookupItem) {
        return "Fubar.Bar".equals(lookupItem.getLookupString());
      }
    }));
    select('\n');
    checkResultByTestName();
  }

  public void testTabAfterNew() throws Exception {
    configureByTestName();
    select('\t');
    checkResultByTestName();
  }

  private void doTest(boolean performAction, boolean selectItem) throws Exception {
    configureByTestName();
    if (performAction) {
      performAction();
    }
    if (selectItem) {
      selectItem(myItems[0]);
    }
    checkResultByTestName();
  }

  private void doActionTest() throws Exception {
    doTest(true, false);
  }

  private void doItemTest() throws Exception {
    doTest(false, true);
  }

  private void doActionItemTest() throws Exception {
    doTest(true, true);
  }

  private void performAction() {
    complete();
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
      configureByTestName();
      if (myItems != null && myItems.length == 1) {
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

  private void checkResultByTestName() throws Exception {
    checkResultByFile(BASE_PATH + "/" + getTestName(false) + "-out.java");
  }

  protected void setUp() throws Exception {
    super.setUp();
    setType(CompletionType.SMART);
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

  private void select() {
    select(Lookup.NORMAL_SELECT_CHAR);
  }

  private static void select(final char c) {
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
