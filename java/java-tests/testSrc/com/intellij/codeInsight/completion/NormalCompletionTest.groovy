package com.intellij.codeInsight.completion;


import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

public class NormalCompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/";
  }

  public void testSimple() throws Exception {
    configureByFile("Simple.java");
    assertStringItems("_local1", "_local2", "_field", "_method", "_baseField", "_baseMethod");
  }

  public void testDontCompleteFieldsAndMethodsInReferenceCodeFragment() throws Throwable {
    final String text = CommonClassNames.JAVA_LANG_OBJECT + ".<caret>";
    PsiFile file = getJavaFacade().getElementFactory().createReferenceCodeFragment(text, null, true, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    complete();
    myFixture.checkResult(text);
    assertEmpty(myItems);
  }

  public void testNoPackagesInExpressionCodeFragment() throws Throwable {
    final String text = "jav<caret>";
    PsiFile file = getJavaFacade().getElementFactory().createExpressionCodeFragment(text, null, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    complete();
    myFixture.checkResult(text);
    assertEmpty(myItems);
  }

  public void testSubPackagesInExpressionCodeFragment() throws Throwable {
    PsiFile file = getJavaFacade().getElementFactory().createExpressionCodeFragment("java.la<caret>", null, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    complete();
    myFixture.checkResult("java.lang.<caret>");
    assertNull(myItems);
  }

  public void testQualifierCastingInExpressionCodeFragment() throws Throwable {
    final ctxText = "class Bar {{ Object o; o=null }}"
    final ctxFile = createLightFile(StdFileTypes.JAVA, ctxText)
    final context = ctxFile.findElementAt(ctxText.indexOf("o="))
    assert context

    PsiFile file = javaFacade.elementFactory.createExpressionCodeFragment("o instanceof String && o.subst<caret>", context, null, true);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    complete();
    myFixture.checkResult("o instanceof String && ((String) o).substring(<caret>)");
    assertNull(myItems);
  }

  private JavaPsiFacade getJavaFacade() {
    return JavaPsiFacade.getInstance(getProject());
  }

  public void testCastToPrimitive1() throws Exception {
    configureByFile("CastToPrimitive1.java");

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    assertTrue(false);
  }

  public void testCastToPrimitive2() throws Exception {
    configureByFile("CastToPrimitive2.java");

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    assertTrue(false);
  }

  public void testCastToPrimitive3() throws Exception {
    configureByFile("CastToPrimitive3.java");

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    assertTrue(false);
  }

  public void testWriteInInvokeLater() throws Exception {
    configureByFile("WriteInInvokeLater.java");
  }

  public void testQualifiedNew1() throws Exception {
    configure()
    assertStringItems "IInner", "Inner"
  }

  public void testQualifiedNew2() throws Exception {
    configure()
    assertStringItems "AnInner", "Inner"
  }

  public void testKeywordsInName() throws Exception {
    doTest 'a\n'
  }

  public void testSimpleVariable() throws Exception { doTest() }

  public void testMethodItemPresentation() {
    configure()
    def presentation = new LookupElementPresentation()
    myItems[0].renderElement(presentation)
    assert "equals" == presentation.itemText
    assert "(Object anObject)" == presentation.tailText
    assert "boolean" == presentation.typeText

    assert !presentation.tailGrayed
    assert presentation.itemTextBold
  }

  public void testPreferLongerNamesOption() throws Exception {
    configureByFile("PreferLongerNamesOption.java");

    assertEquals(3, myItems.length);
    assertEquals("abcdEfghIjk", myItems[0].getLookupString());
    assertEquals("efghIjk", myItems[1].getLookupString());
    assertEquals("ijk", myItems[2].getLookupString());

    LookupManager.getInstance(getProject()).hideActiveLookup();

    CodeStyleSettingsManager.getSettings(getProject()).PREFER_LONGER_NAMES = false;
    try{
      configureByFile("PreferLongerNamesOption.java");

      assertEquals(3, myItems.length);
      assertEquals("ijk", myItems[0].getLookupString());
      assertEquals("efghIjk", myItems[1].getLookupString());
      assertEquals("abcdEfghIjk", myItems[2].getLookupString());
    }
    finally{
      CodeStyleSettingsManager.getSettings(getProject()).PREFER_LONGER_NAMES = true;
    }
  }

  public void testSCR7208() throws Exception {
    configureByFile("SCR7208.java");
  }

  public void testProtectedFromSuper() throws Exception {
    configureByFile("ProtectedFromSuper.java");
    Arrays.sort(myItems);
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "xxx") > 0);
  }

  public void testBeforeInitialization() throws Exception {
    configureByFile("BeforeInitialization.java");
    assertNotNull(myItems);
    assertTrue(myItems.length > 0);
  }

  public void testProtectedFromSuper2() throws Exception {

    configureByFile("ProtectedFromSuper.java");
    Arrays.sort(myItems);
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "xxx") > 0);
  }

  public void testClassLiteralInArrayAnnoInitializer() throws Throwable { doTest(); }
  public void testClassLiteralInArrayAnnoInitializer2() throws Throwable { doTest(); }

  public void testReferenceParameters() throws Exception {
    configureByFile("ReferenceParameters.java");
    assertNotNull(myItems);
    assertEquals(myItems.length, 2);
    assertEquals(myItems[0].getLookupString(), "AAAA");
    assertEquals(myItems[1].getLookupString(), "AAAB");
  }

  public void testConstructorName1() throws Exception{
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    final boolean autocomplete_on_code_completion = settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    configureByFile("ConstructorName1.java");
    assertNotNull(myItems);
    boolean failed = true;
    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("ABCDE")) {
        failed = false;
      }
    }
    assertFalse(failed);
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autocomplete_on_code_completion;
  }

  public void testConstructorName2() throws Exception{
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    final boolean autocomplete_on_code_completion = settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    configureByFile("ConstructorName2.java");
    assertNotNull(myItems);
    boolean failed = true;
    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("ABCDE")) {
        failed = false;
      }
    }
    assertFalse(failed);
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = autocomplete_on_code_completion;
  }

  public void testObjectsInThrowsBlock() throws Exception {
    configureByFile("InThrowsCompletion.java");

    Arrays.sort(myItems);
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "C") > 0);
    assertFalse("Found not an Exception", Arrays.binarySearch(myItems, "B") > 0);
  }

  public void testAfterInstanceof() throws Exception {
    configureByFile("AfterInstanceof.java");

    assertNotNull(myItems);
    Arrays.sort(myItems);
    assertTrue("Classes not found after instanceof", Arrays.binarySearch(myItems, "A") >= 0);
  }

  public void testAfterCast1() throws Exception {
    configureByFile("AfterCast1.java");

    assertNotNull(myItems);
    assertEquals(2, myItems.length);
  }

  public void testAfterCast2() throws Exception {
    configureByFile("AfterCast2.java");
    checkResultByFile("AfterCast2-result.java");
  }

  public void testMethodCallForTwoLevelSelection() throws Exception {
    configureByFile("MethodLookup.java");
    assertEquals(2, myItems.length);
  }

  public void testMethodCallBeforeAnotherStatementWithParen() throws Exception {
    configureByFile("MethodLookup2.java");
    checkResultByFile("MethodLookup2_After.java");
  }

  public void testMethodCallBeforeAnotherStatementWithParen2() throws Exception {
    CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings();
    boolean oldvalue = settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE;
    settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    configureByFile("MethodLookup2.java");
    checkResultByFile("MethodLookup2_After2.java");
    settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = oldvalue;
  }

  public void testSwitchEnumLabel() throws Exception {
    configureByFile("SwitchEnumLabel.java");
    assertEquals(3, myItems.length);
  }

  public void testMethodInAnnotation() throws Exception {
    configureByFile("Annotation.java");
    checkResultByFile("Annotation_after.java");
  }

  public void testMethodInAnnotation2() throws Exception {
    configureByFile("Annotation2.java");
    checkResultByFile("Annotation2_after.java");
  }

  public void testMethodInAnnotation3() throws Exception {
    configureByFile("Annotation3.java");
    checkResultByFile("Annotation3_after.java");
  }

  public void testMethodInAnnotation5() throws Exception {
    configureByFile("Annotation5.java");
    checkResultByFile("Annotation5_after.java");
  }

  public void testMethodInAnnotation7() throws Exception {
    configureByFile("Annotation7.java");
    selectItem(myItems[0]);
    checkResultByFile("Annotation7_after.java");
  }

  public void testEnumInAnnotation() throws Exception {
    configureByFile("Annotation4.java");
    checkResultByFile("Annotation4_after.java");
  }

  public void testSecondAttribute() throws Exception {
    configureByFile("Annotation6.java");
    checkResultByFile("Annotation6_after.java");
  }

  public void testIDEADEV6408() throws Exception {
    configureByFile("IDEADEV6408.java");
    assertEquals(2, myItems.length);
  }

  public void testMethodWithLeftParTailType() throws Exception {
    configureByFile("MethodWithLeftParTailType.java");
    type('(');
    checkResultByFile("MethodWithLeftParTailType_after.java");

    configureByFile("MethodWithLeftParTailType2.java");
    type('(');
    checkResultByFile("MethodWithLeftParTailType2_after.java");
  }

  public void testMethodWithLeftParTailTypeNoPairBrace() throws Exception {
    final boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;

    try {
      configureByFile(getTestName(false) + ".java");
      type('(');
      checkResult()
    }
    finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = old;
    }
  }

  public void testMethodWithLeftParTailTypeNoPairBrace2() throws Exception {
    final boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;

    try {
      //no tail type should work the normal way
      configureByFile("MethodWithLeftParTailTypeNoPairBrace.java");
      selectItem(myItems[0]);
      checkResultByFile("MethodWithLeftParTailTypeNoPairBrace_after2.java");
    }
    finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = old;
    }
  }

  public void testMethodNoPairBrace() throws Exception {
    final boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;

    try {
      doTest '\n'
    }
    finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = old;
    }
  }

  public void testExcessSpaceInTypeCast() throws Throwable {
   configure()
   selectItem(myItems[0]);
   checkResult()
  }

  public void testFieldType() throws Throwable { doTest(); }

  public void testPackageInAnnoParam() throws Throwable {
    doTest();
  }

  public void testClassLiteralInAnnoParam() throws Throwable {
    doTest();
  }

  public void testAtUnderClass() throws Throwable {
    doTest();
  }

  public void testLocalClassName() throws Throwable { doTest(); }
  public void testAssigningFieldForTheFirstTime() throws Throwable { doTest(); }

  public void testClassTypeParameters() throws Throwable {
    configure()
    assert 'K' in myFixture.lookupElementStrings
  }

  public void testClassTypeParametersGenericBounds() throws Throwable {
    configure()
    assert 'K' in myFixture.lookupElementStrings
  }

  public void testLocalClassTwice() throws Throwable {
    configure()
    assertOrderedEquals myFixture.lookupElementStrings, 'Zoooz', 'Zooooo'
  }

  public void testLocalTopLevelConflict() throws Throwable {
    configure()
    assertOrderedEquals myFixture.lookupElementStrings, 'Zoooz', 'Zooooo'
  }

  public void testFinalBeforeMethodCall() throws Throwable {
    configure()
    assertStringItems 'final', 'finalize'
  }

  public void testMethodParenthesesSpaces() throws Throwable {
    final settings = CodeStyleSettingsManager.getSettings(getProject())
    settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    doTest();
  }

  public void testMethodParenthesesSpacesArgs() throws Throwable {
    final settings = CodeStyleSettingsManager.getSettings(getProject())
    settings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    settings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    doTest();
  }

  public void testAtUnderClassNoModifiers() throws Throwable {
    doTest();
  }

  public void testNoThisInComment() throws Throwable { doAntiTest() }

  public void testLastExpressionInFor() throws Throwable { doTest(); }

  public void testUndoCommonPrefixOnHide() throws Throwable {//actually don't undo
    configureByFile(getTestName(false) + ".java");
    checkResult()
    LookupManager.getInstance(getProject()).hideActiveLookup();
    checkResult()
  }

  public void testOnlyKeywordsInsideSwitch() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("case", "default");
  }

  public void testBooleanLiterals() throws Throwable {
    doTest();
  }

  public void testNotOnlyKeywordsInsideSwitch() throws Throwable {
    doTest();
  }

  public void testChainedCallOnNextLine() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResult()
  }

  public void testFinishWithDot() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('.');
    checkResult()
  }

  public void testEnclosingThis() throws Throwable { doTest(); }

  public void testSeamlessConstant() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResult()
  }

  public void testDefaultAnnoParam() throws Throwable { doTest(); }

  public void testSpaceAfterLookupString() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type(' ');
    assertNull(getLookup());
    checkResult()
  }

  public void testNoSpaceInParensWithoutParams() throws Throwable {
    CodeStyleSettingsManager.getSettings(getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    try {
      doTest();
    }
    finally {
      CodeStyleSettingsManager.getSettings(getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    }
  }
  
  public void testTwoSpacesInParensWithParams() throws Throwable {
    CodeStyleSettingsManager.getSettings(getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    try {
      doTest();
    }
    finally {
      CodeStyleSettingsManager.getSettings(getProject()).SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    }
  }

  public void testFillCommonPrefixOnSecondCompletion() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('g');
    complete();
    checkResult()
    assertStringItems("getBar", "getFoo", "getClass");
  }

  public void testQualifierAsPackage() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResult()
  }

  public void testQualifierAsPackage2() throws Throwable {
    doTest();
  }
  
  public void testQualifierAsPackage3() throws Throwable {
    doTest();
  }
  
  public void testPackageNamedVariableBeforeAssignment() throws Throwable {
    doTest();
  }

  public void testFieldWithCastingCaret() throws Throwable { doTest(); }

  public void testInnerEnumConstant() throws Throwable { doTest('\n'); }

  public void testMethodReturnType() throws Throwable {
    doTest();
  }

  public void testMethodReturnTypeNoSpace() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResult()
  }

  public void testEnumWithoutConstants() throws Throwable {
    doTest();
  }

  public void testDoWhileMethodCall() throws Throwable {
    doTest();
  }

  public void testSecondTypeParameterExtends() throws Throwable {
    doTest();
  }

  public void testGetterWithExistingNonEmptyParameterList() throws Throwable {
    doTest();
  }

  public void testNothingAfterNumericLiteral() throws Throwable { doAntiTest(); }

  public void testSpacesAroundEq() throws Throwable { doTest('='); }

  public void testNoAllClassesOnQualifiedReference() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertEmpty(myItems);
    checkResultByFile(getTestName(false) + ".java");
  }

  public void testFinishClassNameWithDot() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('.');
    checkResult()
  }

  public void testFinishClassNameWithLParen() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('(');
    checkResult()
  }

  public void testSelectNoParameterSignature() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    final int parametersCount = ((PsiMethod)getLookup().getCurrentItem().getObject()).getParameterList().getParametersCount();
    assertEquals(0, parametersCount);
    type '\n'
    checkResult()
  }

  public void testCompletionInsideClassLiteral() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    new WriteCommandAction.Simple(getProject(), new PsiFile[0]) {
      @Override
      protected void run() throws Throwable {
        getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR);
      }
    }.execute().throwException();
    checkResult()
  }

  public void testFieldNegation() throws Throwable { doTest('!');}
  public void testDefaultInSwitch() throws Throwable { doTest()}

  public void testSuperInConstructor() throws Throwable {
    doTest();
  }

  public void testSuperInConstructorWithParams() throws Throwable {
    doTest();
  }

  public void testSuperInMethod() throws Throwable {
    doTest();
  }

  public void testSecondMethodParameterName() throws Throwable {
    doTest();
  }

  public void testAnnotationAsUsualObject() throws Throwable {
    doTest();
  }

  public void testAnnotationAsUsualObjectFromJavadoc() throws Throwable {
    doTest();
  }

  public void testAnnotationAsUsualObjectInsideClass() throws Throwable {
    doTest();
  }

  public void testAnnotationOnNothingParens() throws Throwable {
    doTest();
  }

  public void testMultiResolveQualifier() throws Throwable {
    doTest();
  }

  public void testSecondMethodParameter() throws Throwable { doTest(); }

  public void testAnnotationWithoutValueMethod() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("bar", "foo");
  }

  public void testUnnecessaryMethodMerging() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("fofoo", "fofoo");
  }

  public void testDontCancelPrefixOnTyping() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    type('~');
    assertNull(getLookup());
    checkResult()
  }

  public void testAnnotationQualifiedName() throws Throwable {
    doTest();
  }

  public void testClassNameGenerics() throws Throwable {
    configure()
    type  '\n'
    checkResult();
  }

  public void testClassNameAnonymous() throws Throwable {
    configure()
    type  '\n'
    checkResult();
  }

  public void testClassNameWithInner() throws Throwable { doTest() }
  public void testClassNameWithInner2() throws Throwable { doTest() }

  public void testClassNameWithInstanceInner() throws Throwable { doTest('\n') }

  public void testDoubleFalse() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("false", "fefefef");
  }

  public void testSameNamedVariableInNestedClasses() throws Throwable {
    doTest();
    assertNull(getLookup());
  }

  public void testHonorUnderscoreInPrefix() throws Throwable {
    doTest();
  }

  public void testNoSemicolonAfterExistingParenthesesEspeciallyIfItsACast() throws Throwable { doTest(); }
  public void testReturningTypeVariable() throws Throwable { doTest(); }
  public void testReturningTypeVariable2() throws Throwable { doTest(); }
  public void testReturningTypeVariable3() throws Throwable { doTest(); }
  public void testImportInGenericType() throws Throwable { doTest(); }

  public void testCaseTailType() throws Throwable { doTest(); }

  def doPrimitiveTypeTest() {
    configure()
    checkResultByFile(getTestName(false) + ".java");
    assertTrue 'boolean' in myFixture.lookupElementStrings
  }

  private def configure() {
    configureByFile(getTestName(false) + ".java")
  }

  public void testFinalInForLoop() throws Throwable {
    configure()
    assertStringItems 'final'
  }

  public void testFinalInForLoop2() throws Throwable {
    configure()
    assertStringItems 'final', 'finalize'
  }

  public void testPrimitiveTypesInForLoop() throws Throwable { doPrimitiveTypeTest() }
  public void testPrimitiveTypesInForLoop2() throws Throwable { doPrimitiveTypeTest() }
  public void testPrimitiveTypesInForLoop3() throws Throwable { doPrimitiveTypeTest() }
  public void testPrimitiveTypesInForLoop4() throws Throwable { doPrimitiveTypeTest() }
  public void testPrimitiveTypesInForLoop5() throws Throwable { doPrimitiveTypeTest() }
  public void testPrimitiveTypesInForLoop6() throws Throwable { doPrimitiveTypeTest() }

  public void testPrimitiveTypesInForLoopSpace() throws Throwable {
    configure()
    myFixture.type ' '
    checkResultByFile(getTestName(false) + "_after.java")
  }

  public void testSecondInvocationToFillCommonPrefix() throws Throwable {
    configure()
    type('a');
    complete();
    assertStringItems("fai1", "fai2", "fai3");
    checkResult()
  }

  public void testSuggestInaccessibleOnSecondInvocation() throws Throwable {
    configure()
    assertStringItems("_bar", "_goo");
    complete();
    assertStringItems("_bar", "_goo", "_foo");
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    selectItem(lookup.items[2], Lookup.NORMAL_SELECT_CHAR)
    checkResult()
  }

  public void testNoCommonPrefixInsideIdentifier() throws Throwable {
    final String path = getTestName(false) + ".java";
    configureByFile(path);
    checkResultByFile(path);
    assertStringItems("fai1", "fai2");
  }

  public void testProtectedInaccessibleOnSecondInvocation() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    checkResult()
  }

  public void testPropertyReferencePrefix() throws Throwable {
    myFixture.addFileToProject("test.properties", "foo.bar=Foo! Bar!").getVirtualFile();
    doAntiTest()
  }

  private void doTest() throws Exception {
    configure()
    checkResult();
  }

  private void doTest(String finishChar) throws Exception {
    configure()
    type finishChar
    checkResult();
  }

  private void doAntiTest() throws Exception {
    configure()
    checkResultByFile(getTestName(false) + ".java");
    assertEmpty(myItems);
    assertNull(getLookup());
  }

  public void testSecondAnonymousClassParameter() throws Throwable { doTest(); }

  public void testSpaceAfterReturn() throws Throwable {
    configure()
    type '\n'
    checkResult()
  }

  private def checkResult() {
    checkResultByFile(getTestName(false) + "_after.java")
  }

  public void testIntersectionTypeMembers() throws Throwable {
    configure()
    assertStringItems "fooa", "foob"
  }

  public void testCastInstanceofedQualifier() throws Throwable { doTest(); }
  public void testCastComplexInstanceofedQualifier() throws Throwable { doTest(); }

  public void testCastTooComplexInstanceofedQualifier() throws Throwable { doAntiTest(); }
  public void testDontCastInstanceofedQualifier() throws Throwable { doTest(); }
  public void testQualifierCastingWithUnknownAssignments() throws Throwable { doTest(); }
  public void testQualifierCastingBeforeLt() throws Throwable { doTest(); }

  public void testOrAssignmentDfa() throws Throwable { doTest(); }

  public void testWildcardsInLookup() throws Exception {
    configure()
    assertNotNull(getLookup());
    type('*fz');
    final List<LookupElement> list = getLookup().getItems();
    assertEquals("azzzfzzz", list.get(0).getLookupString());
    assertEquals("fzazzz", list.get(1).getLookupString());
  }

  public void testMethodParameterAnnotationClass() throws Throwable { doTest(); }

  public void testVoidMethodsInNonVoidContext() throws Throwable {
    configure()
    checkResultByFile(getTestName(false) + ".java")
    assertEmpty(myItems)
    assertNull(getLookup());

    myFixture.complete(CompletionType.BASIC, 2)
    checkResult()
  }

  public void testEnumConstantFromEnumMember() throws Throwable { doTest(); }

  public void testPrimitiveMethodParameter() throws Throwable { doTest(); }

  public void testNewExpectedClassParens() throws Throwable { doTest(); }

  public void testQualifyInnerMembers() throws Throwable { doTest('\n') }

  public void testSuggestExpectedTypeMembers() throws Throwable { doTest('\n') }

  public void testSuggestExpectedTypeMembersInCall() throws Throwable { doTest('\n') }

  public void testSuggestExpectedTypeMembersNonImported() throws Throwable {
    myFixture.addClass("package foo; public class Super { public static final Super FOO = null; }")
    myFixture.addClass("package foo; public class Usage { public static void foo(Super s) {} }")
    doTest('\n')
  }

  public void testClassNameWithInnersTab() throws Throwable { doTest('\t') }

  public void testClassNameWithGenericsTab() throws Throwable {doTest('\t') }
  public void testClassNameWithGenericsTab2() throws Throwable {doTest('\t') }

  public void testLiveTemplatePrefixTab() throws Throwable {doTest('\t') }

  public void testOnlyAnnotationsAfterAt() throws Throwable { doTest() }

  public void testOnlyExceptionsInCatch1() throws Exception { doTest() }
  public void testOnlyExceptionsInCatch2() throws Exception { doTest() }
  public void testOnlyExceptionsInCatch3() throws Exception { doTest() }
  public void testOnlyExceptionsInCatch4() throws Exception { doTest() }

  public void testCommaAfterVariable() throws Throwable { doTest(',') }

  public void testClassAngleBracket() throws Throwable { doTest('<') }

  public void testClassSquareBracket() throws Throwable { doTest('[') }
  public void testPrimitiveSquareBracket() throws Throwable { doTest('[') }
  public void testVariableSquareBracket() throws Throwable { doTest('[') }
  public void testMethodSquareBracket() throws Throwable { doTest('[') }

  public void testMethodParameterTypeDot() throws Throwable { doAntiTest() }

  public void testSuperProtectedMethod() throws Throwable {
    myFixture.addClass """package foo;
      public class Bar {
          protected void foo() { }
      }"""
    doTest()
  }

  public void testTopLevelClassesFromPackaged() throws Throwable {
    myFixture.addClass "public class Fooooo {}"
    final text = "package foo; class Bar { Fooo<caret> }"
    def file = myFixture.addFileToProject("foo/Bar.java", text)
    myFixture.configureFromExistingVirtualFile file.virtualFile
    assertEmpty myFixture.completeBasic()
    myFixture.checkResult text
  }

  public void testRightShift() throws Throwable {
    configure()
    assertStringItems("myField1", "myField2");
  }

  public void testAfterCommonPrefix() throws Throwable {
    configure()
    type 'eq'
    assertStringItems("equals", "equalsIgnoreCase");
    complete()
    assertStringItems("equals", "equalsIgnoreCase");
    type '('
    checkResult()
  }

  public void testClassNameInsideIdentifierInIf() throws Throwable {
    doTest '\n'
  }

  public void testSuggestMembersOfStaticallyImportedClasses() throws Exception {
    myFixture.addClass("""package foo;
    public class Foo {
      public static void foo() {}
      public static void bar() {}
    }
    """)
    doTest()
  }

  public void testSuggestMembersOfStaticallyImportedClassesUnqualifiedOnly() throws Exception {
    def old = CodeInsightSettings.instance.SHOW_STATIC_AFTER_INSTANCE
    CodeInsightSettings.instance.SHOW_STATIC_AFTER_INSTANCE = true

    try {
      myFixture.addClass("""package foo;
      public class Foo {
        public static void foo() {}
        public static void bar() {}
      }
      """)
      configure()
      assertOneElement(myFixture.getLookupElements())
      myFixture.type '\t'
      checkResult()
    }
    finally {
      CodeInsightSettings.instance.SHOW_STATIC_AFTER_INSTANCE = old
    }
  }

  public void testInstanceMagicMethod() throws Exception { doTest() }

  public void testNoDotOverwrite() throws Exception { doTest('.') }

  public void testStaticInnerExtendingOuter() throws Exception { doTest() }

  public void testNewClassAngleBracket() throws Exception { doTest('<') }
  public void testNewClassSquareBracket() throws Exception { doTest('[') }

  public void testMethodColon() throws Exception { doTest(':') }
  public void testVariableColon() throws Exception { doTest(':') }

  public void testNoMethodsInParameterType() {
    configure()
    assertOrderedEquals myFixture.lookupElementStrings, "final", "float"
  }

  public void testStaticallyImportedFieldsTwice() {
    myFixture.addClass("""
      class Foo {
        public static final int aZOO;
      }
    """)
    myFixture.configureByText("a.java", """
      import static Foo.*
      class Bar {{
        aZ<caret>a
      }}
    """)
    assertOneElement myFixture.completeBasic()
  }

  public void testStatementKeywords() {
    myFixture.configureByText("a.java", """
      class Bar {{
        <caret>xxx
      }}
    """)
    myFixture.completeBasic()
    final def strings = myFixture.lookupElementStrings
    assertTrue 'if' in strings
    assertTrue 'while' in strings
    assertTrue 'do' in strings
    assertTrue 'new' in strings
    assertTrue 'try' in strings

    strings.remove 'new'
    assertFalse 'new' in strings
  }

  public void testExpressionKeywords() {
    myFixture.configureByText("a.java", """
      class Bar {{
        foo(<caret>xxx)
      }}
    """)
    myFixture.completeBasic()
    final def strings = myFixture.lookupElementStrings
    assertTrue 'new' in strings
  }

  public void testImportAsterisk() {
    myFixture.configureByText "a.java", "import java.lang.<caret>"
    myFixture.completeBasic()
    myFixture.type '*\n'
    myFixture.checkResult "import java.lang.*<caret>"
  }
}
