package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

import java.util.Arrays;
import java.util.List;

public class NormalCompletionTest extends LightCompletionTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimple() throws Exception {                              
    configureByFile("/codeInsight/completion/normal/Simple.java");
    assertEquals("_", myPrefix);
    testByCount(6, "_local2", "_local1", "_field", "_method", "_baseField", "_baseMethod");
  }

  public void testDontCompleteFieldsAndMethodsInReferenceCodeFragment() throws Throwable {
    final String text = CommonClassNames.JAVA_LANG_OBJECT + ".";
    myFile = getJavaFacade().getElementFactory().createReferenceCodeFragment(text, null, true, true);
    myEditor = new EditorImpl(PsiDocumentManager.getInstance(getProject()).getDocument(myFile), false, getProject());
    myEditor.getCaretModel().moveToOffset(text.length());
    complete();
    assertEquals(text, myEditor.getDocument().getText());
    assertNull(myItems);
  }

  public void testCastToPrimitive1() throws Exception {
    configureByFile("/codeInsight/completion/normal/CastToPrimitive1.java");

    assertEquals("", myPrefix);
    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    assertTrue(false);
  }

  public void testCastToPrimitive2() throws Exception {
    configureByFile("/codeInsight/completion/normal/CastToPrimitive2.java");

    assertEquals("", myPrefix);
    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    assertTrue(false);
  }

  public void testCastToPrimitive3() throws Exception {
    configureByFile("/codeInsight/completion/normal/CastToPrimitive3.java");

    assertEquals("", myPrefix);
    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return;
    }
    assertTrue(false);
  }

  public void testWriteInInvokeLater() throws Exception {
    configureByFile("/codeInsight/completion/normal/WriteInInvokeLater.java");
  }

  public void testQualifiedNew1() throws Exception {
    configureByFile("/codeInsight/completion/normal/QualifiedNew1.java");

    assertEquals("I", myPrefix);
    assertEquals(2, myItems.length);
    assertEquals("IInner", myItems[0].getLookupString());
    assertEquals("Inner", myItems[1].getLookupString());
  }

  public void testQualifiedNew2() throws Exception {
    configureByFile("/codeInsight/completion/normal/QualifiedNew2.java");

    assertEquals("", myPrefix);
    assertEquals(2, myItems.length);
    assertEquals("AnInner", myItems[0].getLookupString());
    assertEquals("Inner", myItems[1].getLookupString());
  }

  public void testKeywordsInName() throws Exception {
    configureByFile("/codeInsight/completion/normal/KeywordsInName.java");
    checkResultByFile("/codeInsight/completion/normal/KeywordsInName_after.java");
  }

  public void testSimpleVariable() throws Exception {
    configureByFile("/codeInsight/completion/normal/SimpleVariable.java");
    checkResultByFile("/codeInsight/completion/normal/SimpleVariable_after.java");
  }

  public void testPreferLongerNamesOption() throws Exception {
    configureByFile("/codeInsight/completion/normal/PreferLongerNamesOption.java");

    assertEquals("", myPrefix);
    assertEquals(3, myItems.length);
    assertEquals("abcdEfghIjk", myItems[0].getLookupString());
    assertEquals("efghIjk", myItems[1].getLookupString());
    assertEquals("ijk", myItems[2].getLookupString());

    LookupManager.getInstance(getProject()).hideActiveLookup();

    CodeStyleSettingsManager.getSettings(getProject()).PREFER_LONGER_NAMES = false;
    try{
      configureByFile("/codeInsight/completion/normal/PreferLongerNamesOption.java");

      assertEquals("", myPrefix);
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
    configureByFile("/codeInsight/completion/normal/SCR7208.java");
  }

  public void testProtectedFromSuper() throws Exception {
    configureByFile("/codeInsight/completion/normal/ProtectedFromSuper.java");
    Arrays.sort(myItems);
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "xxx") > 0);
  }

  public void testBeforeInitialization() throws Exception {
    configureByFile("/codeInsight/completion/normal/BeforeInitialization.java");
    assertNotNull(myItems);
    assertTrue(myItems.length > 0);
  }

  public void testProtectedFromSuper2() throws Exception {

    configureByFile("/codeInsight/completion/normal/ProtectedFromSuper.java");
    Arrays.sort(myItems);
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "xxx") > 0);
  }

  public void testReferenceParameters() throws Exception {
    configureByFile("/codeInsight/completion/normal/ReferenceParameters.java");
    assertNotNull(myItems);
    assertEquals(myItems.length, 2);
    assertEquals(myItems[0].getLookupString(), "AAAA");
    assertEquals(myItems[1].getLookupString(), "AAAB");
  }

  public void testConstructorName1() throws Exception{
    final CodeInsightSettings settings = CodeInsightSettings.getInstance();
    final boolean autocomplete_on_code_completion = settings.AUTOCOMPLETE_ON_CODE_COMPLETION;
    settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    configureByFile("/codeInsight/completion/normal/ConstructorName1.java");
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
    configureByFile("/codeInsight/completion/normal/ConstructorName2.java");
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
    configureByFile("/codeInsight/completion/normal/InThrowsCompletion.java");

    assertEquals("", myPrefix);
    Arrays.sort(myItems);
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "C") > 0);
    assertFalse("Found not an Exception", Arrays.binarySearch(myItems, "B") > 0);
  }

  public void testAfterInstanceof() throws Exception {
    configureByFile("/codeInsight/completion/normal/AfterInstanceof.java");

    assertEquals("", myPrefix);
    assertNotNull(myItems);
    Arrays.sort(myItems);
    assertTrue("Classes not found after instanceof", Arrays.binarySearch(myItems, "A") >= 0);
  }

  public void testAfterCast1() throws Exception {
    configureByFile("/codeInsight/completion/normal/AfterCast1.java");

    assertNotNull(myItems);
    assertEquals(2, myItems.length);
  }

  public void testAfterCast2() throws Exception {
    configureByFile("/codeInsight/completion/normal/AfterCast2.java");
    checkResultByFile("/codeInsight/completion/normal/AfterCast2-result.java");
  }

  public void testMethodCallForTwoLevelSelection() throws Exception {
    configureByFile("/codeInsight/completion/normal/MethodLookup.java");
    assertEquals(2, myItems.length);
  }

   public void testMethodCallBeforeAnotherStatementWithParen() throws Exception {
     configureByFile("/codeInsight/completion/normal/MethodLookup2.java");
     checkResultByFile("/codeInsight/completion/normal/MethodLookup2_After.java");

     CodeStyleSettings settings = CodeStyleSettingsManager.getInstance(getProject()).getCurrentSettings();
     boolean oldvalue = settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE;
     settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
     configureByFile("/codeInsight/completion/normal/MethodLookup2.java");
     checkResultByFile("/codeInsight/completion/normal/MethodLookup2_After2.java");
     settings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = oldvalue;
  }

  public void testSwitchEnumLabel() throws Exception {
    configureByFile("/codeInsight/completion/normal/SwitchEnumLabel.java");
    assertEquals(3, myItems.length);
  }

  public void testMethodInAnnotation() throws Exception {
    configureByFile("/codeInsight/completion/normal/Annotation.java");
    checkResultByFile("/codeInsight/completion/normal/Annotation_after.java");
  }

  public void testMethodInAnnotation2() throws Exception {
    configureByFile("/codeInsight/completion/normal/Annotation2.java");
    checkResultByFile("/codeInsight/completion/normal/Annotation2_after.java");
  }

  public void testMethodInAnnotation3() throws Exception {

    configureByFile("/codeInsight/completion/normal/Annotation3.java");
    checkResultByFile("/codeInsight/completion/normal/Annotation3_after.java");
  }

  public void testMethodInAnnotation5() throws Exception {

    configureByFile("/codeInsight/completion/normal/Annotation5.java");
    checkResultByFile("/codeInsight/completion/normal/Annotation5_after.java");
  }

  public void testMethodInAnnotation7() throws Exception {

    configureByFile("/codeInsight/completion/normal/Annotation7.java");
    selectItem(myItems[0]);
    checkResultByFile("/codeInsight/completion/normal/Annotation7_after.java");
  }

  public void testEnumInAnnotation() throws Exception {
    configureByFile("/codeInsight/completion/normal/Annotation4.java");
    checkResultByFile("/codeInsight/completion/normal/Annotation4_after.java");
  }

  public void testSecondAttribute() throws Exception {
    configureByFile("/codeInsight/completion/normal/Annotation6.java");
    checkResultByFile("/codeInsight/completion/normal/Annotation6_after.java");
  }

  public void testIDEADEV6408() throws Exception {
    configureByFile("/codeInsight/completion/normal/IDEADEV6408.java");
    assertEquals(2, myItems.length);
  }

  public void testMethodWithLeftParTailType() throws Exception {
    configureByFile("/codeInsight/completion/normal/MethodWithLeftParTailType.java");
    selectItem(myItems[0], '(');
    checkResultByFile("/codeInsight/completion/normal/MethodWithLeftParTailType_after.java");

    configureByFile("/codeInsight/completion/normal/MethodWithLeftParTailType2.java");
    selectItem(myItems[0], '(');
    checkResultByFile("/codeInsight/completion/normal/MethodWithLeftParTailType2_after.java");
  }

  public void testMethodWithLeftParTailTypeNoPairBrace() throws Exception {
    final boolean old = CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET;
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false;

    try {
      configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
      selectItem(myItems[0], '(');
      checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");

      //no tail type should work the normal way
      configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
      selectItem(myItems[0]);
      checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after2.java");
    }
    finally {
      CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = old;
    }
  }

  public void testExcessSpaceInTypeCast() throws Throwable {
   configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
   selectItem(myItems[0]);
   checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testPackageInAnnoParam() throws Throwable {
    doTest();
  }
  
  public void testClassLiteralInAnnoParam() throws Throwable {
    doTest();
  }

  public void testAtUnderClass() throws Throwable {
    doTest();
  }

  public void testAtUnderClassNoModifiers() throws Throwable {
    doTest();
  }

  public void testLastExpressionInFor() throws Throwable { doTest(); }

  public void testUndoCommonPrefixOnHide() throws Throwable {//actually don't undo
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
    LookupManager.getInstance(getProject()).hideActiveLookup();
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testOnlyKeywordsInsideSwitch() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertStringItems("case", "default");
  }

  public void testBooleanLiterals() throws Throwable {
    doTest();
  }

  public void testNotOnlyKeywordsInsideSwitch() throws Throwable {
    doTest();
  }

  public void testChainedCallOnNextLine() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java"); 
  }

  public void testFinishWithDot() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    selectItem(myItems[0], '.');
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testEnclosingThis() throws Throwable { doTest(); }

  public void testSeamlessConstant() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testDefaultAnnoParam() throws Throwable { doTest(); }

  public void testSpaceAfterLookupString() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    type(' ');
    assertNull(getLookup());
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
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
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    type('g');
    complete();
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
    assertStringItems("getBar", "getFoo", "getClass");
  }

  public void testQualifierAsPackage() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
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

  public void testMethodReturnType() throws Throwable {
    doTest();
  }

  public void testMethodReturnTypeNoSpace() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    selectItem(myItems[0]);
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
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

  public void testNoAllClassesOnQualifiedReference() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertNull(myItems);
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
  }

  public void testNoAllClassesAutoInsert() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertStringItems("GregorianCalendar");
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
  }

  public void testFinishClassNameWithDot() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    type('.');
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testFinishClassNameWithLParen() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    type('(');
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testSelectNoParameterSignature() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    final int parametersCount = ((PsiMethod)getLookup().getCurrentItem().getObject()).getParameterList().getParametersCount();
    assertEquals(0, parametersCount);
    getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testCompletionInsideClassLiteral() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

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
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertStringItems("bar", "foo");
  }

  public void testUnnecessaryMethodMerging() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertStringItems("fofoo", "fofoo");
  }

  public void testDontCancelPrefixOnTyping() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    type('~');
    assertNull(getLookup());
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testAnnotationQualifiedName() throws Throwable {
    doTest();
  }

  public void testDoubleFalse() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertStringItems("false", "finalize");
  }

  public void testSameNamedVariableInNestedClasses() throws Throwable {
    doTest();
    assertNull(getLookup());
  }

  public void testHonorUnderscoreInPrefix() throws Throwable {
    doTest();
  }

  public void testCaseTailType() throws Throwable { doTest(); }

  public void testSecondInvocationToFillCommonPrefix() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    type('a');
    complete();
    assertStringItems("fai1", "fai2", "fai3");
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testSuggestInaccessibleOnSecondInvocation() throws Throwable {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertStringItems("_bar", "_goo");
    complete();
    assertStringItems("_bar", "_goo", "_foo");
    getLookup().setCurrentItem(getLookup().getItems().get(2));
    getLookup().finishLookup(Lookup.NORMAL_SELECT_CHAR);
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testNoCommonPrefixInsideIdentifier() throws Throwable {
    final String path = "/codeInsight/completion/normal/" + getTestName(false) + ".java";
    configureByFile(path);
    checkResultByFile(path);
    assertStringItems("fai1", "fai2");
  }

  public void testProtectedInaccessibleOnSecondInvocation() throws Throwable {
    configureByFileNoComplete("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    complete(2);
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  public void testPropertyReferencePrefix() throws Throwable {
    final VirtualFile data = getSourceRoot().createChildData(this, "test.properties");
    VfsUtil.saveText(data, "foo.bar=Foo! Bar!");

    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertNull(getLookup());
  }

  private void doTest() throws Exception {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + "_after.java");
  }

  private void doAntiTest() throws Exception {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    checkResultByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertNull(myItems);
    assertNull(getLookup());
  }

  public void testSecondAnonymousClassParameter() throws Throwable { doTest(); }

  public void testCastInstanceofedQualifier() throws Throwable { doTest(); }
  public void testCastComplexInstanceofedQualifier() throws Throwable { doTest(); }

  public void testCastTooComplexInstanceofedQualifier() throws Throwable { doAntiTest(); }
  public void testDontCastInstanceofedQualifier() throws Throwable { doTest(); }

  public void testWildcardsInLookup() throws Exception {
    configureByFile("/codeInsight/completion/normal/" + getTestName(false) + ".java");
    assertNotNull(getLookup());
    type('*');
    type('f');
    type('z');
    final List<LookupElement> list = getLookup().getItems();
    assertEquals("azzzfzzz", list.get(0).getLookupString());
    assertEquals("fzazzz", list.get(1).getLookupString());
  }

  public void testMethodParameterAnnotationClass() throws Throwable { doTest(); }

  public void testEnumConstantFromEnumMember() throws Throwable { doTest(); }

  public void testPrimitiveMethodParameter() throws Throwable { doTest(); }


}
