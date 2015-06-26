/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.completion
import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection

public class NormalCompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/";
  }

  public void testSimple() throws Exception {
    configureByFile("Simple.java");
    assertStringItems("_local1", "_local2", "_field", "_method", "_baseField", "_baseMethod");
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

  public void testSimpleVariable() throws Exception { doTest('\n') }

  public void testTypeParameterItemPresentation() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "Param" == presentation.itemText
    assert presentation.tailText == " (type parameter of Foo)"
    assert !presentation.typeText
    assert !presentation.icon
    assert !presentation.itemTextBold

    presentation = renderElement(myItems[1])
    assert "Param2" == presentation.itemText
    assert presentation.tailText == " (type parameter of goo)"
  }
  
  public void testDisplayDefaultValueInAnnotationMethods() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "myBool" == presentation.itemText
    assert presentation.tailText == " default false"
    assert presentation.tailFragments[0].grayed
    assert !presentation.typeText
    assert !presentation.itemTextBold

    presentation = renderElement(myItems[1])
    assert "myString" == presentation.itemText
    assert presentation.tailText == ' default "unknown"'
  }

  public void testMethodItemPresentation() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "equals" == presentation.itemText
    assert "(Object anObject)" == presentation.tailText
    assert "boolean" == presentation.typeText

    assert !presentation.tailGrayed
    assert presentation.itemTextBold
  }

  private LookupElementPresentation renderElement(LookupElement element) {
    return LookupElementPresentation.renderElement(element)
  }

  public void testFieldItemPresentationGenerics() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "target" == presentation.itemText
    assert !presentation.tailText
    assert "String" == presentation.typeText
  }

  public void testMethodItemPresentationGenerics() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "add" == presentation.itemText
    assert "(int index, String element)" == presentation.tailText
    assert "void" == presentation.typeText

    presentation = renderElement(myItems[1])
    assert "(String o)" == presentation.tailText
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
    myFixture.assertPreferredCompletionItems 0, 'AAAA', 'AAAB'
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.instance.AUTOCOMPLETE_ON_CODE_COMPLETION = true
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    super.tearDown()
  }

  public void testConstructorName1() throws Exception{
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    configure();
    assert 'ABCDE' in myFixture.lookupElementStrings
  }

  public void testConstructorName2() throws Exception{
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    configure();
    assert 'ABCDE' in myFixture.lookupElementStrings
  }

  public void testObjectsInThrowsBlock() throws Exception {
    configureByFile("InThrowsCompletion.java");
    assert "C" == myFixture.lookupElementStrings[0]
    assert "B" in myFixture.lookupElementStrings
  }

  public void testAnnoParameterValue() throws Exception {
    configure()
    def strings = myFixture.lookupElementStrings
    assert 'AssertionError' in strings
    assert !('enum' in strings)
    assert !('final' in strings)
    assert !('equals' in strings)
    assert !('new' in strings)
    assert !('null' in strings)
    assert !('public' in strings)
    assert !('super' in strings)
    assert !('null' in strings)
  }

  public void testAfterInstanceof() throws Exception {
    configureByFile("AfterInstanceof.java");
    assert "A" in myFixture.lookupElementStrings
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
    codeStyleSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true;
    configureByFile("MethodLookup2.java");
    checkResultByFile("MethodLookup2_After2.java");
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
    assertFirstStringItems "boolean", "byte"
  }

  public void testMethodWithLeftParTailType() throws Exception {
    configureByFile("MethodWithLeftParTailType.java");
    type('(');
    checkResultByFile("MethodWithLeftParTailType_after.java");

    configureByFile("MethodWithLeftParTailType2.java");
    type('(');
    checkResultByFile("MethodWithLeftParTailType2_after.java");
  }

  public void testSuperErasure() throws Exception {
    configureByFile("SuperErasure.java");
    checkResultByFile("SuperErasure_after.java");
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

  public void testFieldType() { doTest(); }

  public void testPackageInAnnoParam() throws Throwable {
    doTest();
  }

  public void testAnonymousTypeParameter() throws Throwable { doTest(); }

  public void testClassLiteralInAnnoParam() throws Throwable {
    doTest();
  }

  public void testNoForceBraces() {
    codeStyleSettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    doTest('\n')
  }

  public void testExcludeStringBuffer() throws Throwable {
    CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = [StringBuffer.name] as String[]
    try {
      doAntiTest()
    }
    finally {
      CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = new String[0]
    }
  }

  public void testExcludeInstanceInnerClasses() throws Throwable {
    CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = ['foo']
    myFixture.addClass 'package foo; public class Outer { public class Inner {} }'
    myFixture.addClass 'package bar; public class Inner {}'
    try {
      configure()
      assert 'bar.Inner' == ((JavaPsiClassReferenceElement)myFixture.lookupElements[0]).qualifiedName
      assert myFixture.lookupElementStrings == ['Inner']
    }
    finally {
      CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = new String[0]
    }
  }

  public void testExcludedInstanceInnerClassCreation() throws Throwable {
    CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = ['foo']
    myFixture.addClass 'package foo; public class Outer { public class Inner {} }'
    myFixture.addClass 'package bar; public class Inner {}'
    try {
      configure()
      assert 'foo.Outer.Inner' == ((JavaPsiClassReferenceElement)myFixture.lookupElements[0]).qualifiedName
      assert myFixture.lookupElementStrings == ['Inner']
    }
    finally {
      CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = new String[0]
    }
  }

  public void testExcludedInstanceInnerClassQualifiedReference() throws Throwable {
    CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = ['foo']
    myFixture.addClass 'package foo; public class Outer { public class Inner {} }'
    myFixture.addClass 'package bar; public class Inner {}'
    try {
      configure()
      assert 'foo.Outer.Inner' == ((JavaPsiClassReferenceElement)myFixture.lookupElements[0]).qualifiedName
      assert myFixture.lookupElementStrings == ['Inner']
    }
    finally {
      CodeInsightSettings.getInstance().EXCLUDED_PACKAGES = new String[0]
    }
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
  public void testPrivateInAnonymous() throws Throwable { doTest() }

  public void testMethodParenthesesSpaces() throws Throwable {
    codeStyleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    doTest();
  }

  public void testMethodParenthesesSpacesArgs() throws Throwable {
    codeStyleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    doTest();
  }

  public void testAtUnderClassNoModifiers() throws Throwable {
    doTest();
  }

  public void testBreakInIfCondition() throws Throwable { doTest(); }
  public void testAccessStaticViaInstance() throws Throwable { doTest(); }

  public void testAccessStaticViaInstanceSecond() throws Throwable {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    checkResult()
  }

  public void testAccessInstanceFromStaticSecond() throws Throwable {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    checkResult()
  }

  public void testContinueLabel() throws Throwable { doTest(); }

  public void testAnonymousProcess() {
    myFixture.addClass 'package java.lang; public class Process {}'
    myFixture.addClass '''
import java.util.*;
public class Process {}
interface Pred <A> { boolean predicate(A elem); }
public class ListUtils {
    public static <A> List<A> filter(List<A> list, Pred<A> pred) {}
}
'''
    configure()
    type '\n'
    checkResult()
  }

  public void testNoThisInComment() throws Throwable { doAntiTest() }
  public void testIncNull() throws Throwable {
    configure()
    checkResultByFile(getTestName(false) + ".java")
    assert !('null' in myFixture.lookupElementStrings)
  }

  public void testLastExpressionInFor() throws Throwable { doTest(); }

  public void testOnlyKeywordsInsideSwitch() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("case", "default");
  }

  public void testBooleanLiterals() throws Throwable {
    doTest('\n');
  }

  public void testDoubleBooleanInParameter() throws Throwable {
    configure()
    assertFirstStringItems("boolean", "byte")
  }

  public void testDoubleConstant() throws Throwable {
    configure()
    assertStringItems("XFOO")
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
    codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    try {
      doTest();
    }
    finally {
      codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = false;
    }
  }
  
  public void testTwoSpacesInParensWithParams() throws Throwable {
    codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true;
    doTest();
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

  public void testPreselectEditorSelection() {
    configure()
    assert lookup.currentItem != myFixture.lookupElements[0]
    assert 'finalize' == lookup.currentItem.lookupString
  }

  public void testNoMethodsInNonStaticImports() {
    configure()
    assertStringItems("*")
  }

  public void testMembersInStaticImports() { doTest() }
  
  public void testPackageNamedVariableBeforeAssignment() throws Throwable {
    doTest();
  }

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
  public void testNothingAfterTypeParameterQualifier() { doAntiTest(); }
  public void testExcludeVariableBeingDeclared() { doAntiTest(); }
  public void testExcludeVariableBeingDeclared2() { doAntiTest(); }

  public void testSpacesAroundEq() throws Throwable { doTest('='); }

  public void _testClassBeforeCast() throws Throwable { doTest '\n' }

  public void testNoAllClassesOnQualifiedReference() throws Throwable {
    doAntiTest()
  }

  public void testFinishClassNameWithDot() throws Throwable {
    doTest('.')
  }

  public void testFinishClassNameWithLParen() throws Throwable {
    doTest('(')
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
    type('\n')
    checkResult()
  }

  public void testFieldNegation() throws Throwable { doTest('!');}
  public void testDefaultInSwitch() throws Throwable { doTest()}
  public void testBreakInSwitch() throws Throwable { doTest() }

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
  public void testReturnInCase() throws Throwable { doTest(); }
  public void testUnboxedConstantsInCase() throws Throwable { doTest(); }

  public void testAnnotationWithoutValueMethod() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("bar", "foo");
  }

  public void testAddExplicitValueInAnnotation() throws Throwable {
    configureByTestName()
    assertStringItems("bar", "goo")
    selectItem(myItems[0])
    checkResult()
  }

  public void testUnnecessaryMethodMerging() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertStringItems("fofoo", "fofoo");
  }

  public void testMethodMergingMinimalTail() { doTest() }

  public void testAnnotationQualifiedName() throws Throwable {
    doTest();
  }

  public void testClassNameGenerics() throws Throwable {
    doTest('\n')
  }

  public void testClassNameAnonymous() throws Throwable {
    doTest('\n')
  }

  public void testClassNameWithInner() throws Throwable {
    configure()
    assertStringItems 'Zzoo', 'Zzoo.Impl'
    type '\n'
    checkResult()
  }
  public void testClassNameWithInner2() throws Throwable { doTest('\n') }

  public void testClassNameWithInstanceInner() throws Throwable { doTest('\n') }

  public void testDoubleFalse() throws Throwable {
    configureByFile(getTestName(false) + ".java");
    assertFirstStringItems("fefefef", "false", "float", "finalize");
  }

  public void testSameNamedVariableInNestedClasses() throws Throwable {
    doTest();
  }

  public void testHonorUnderscoreInPrefix() throws Throwable {
    doTest();
  }

  public void testNoSemicolonAfterExistingParenthesesEspeciallyIfItsACast() throws Throwable { doTest(); }
  public void testReturningTypeVariable() throws Throwable { doTest(); }
  public void testReturningTypeVariable2() throws Throwable { doTest(); }
  public void testReturningTypeVariable3() throws Throwable { doTest(); }
  public void testImportInGenericType() throws Throwable {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.type('\n')
    checkResult();
  }

  public void testCaseTailType() throws Throwable { doTest(); }

  def doPrimitiveTypeTest() {
    configure()
    checkResultByFile(getTestName(false) + ".java");
    assertTrue 'boolean' in myFixture.lookupElementStrings
  }

  private def configure() {
    configureByTestName()
  }

  public void testFinalInForLoop() throws Throwable {
    configure()
    assertStringItems 'final'
  }

  public void testFinalInForLoop2() throws Throwable {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'finalize', 'final'
  }

  public void testOnlyClassesInExtends() throws Throwable {
    configure()
    assertStringItems 'Inner'
  }

  public void testNoThisClassInExtends() throws Throwable {
    configure()
    assertStringItems 'Fooxxxx2'
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
    myFixture.type('\n')
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

  public void testSecondAnonymousClassParameter() { doTest(); }

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

  public void testNoReturnInTernary() throws Throwable { doTest(); }

  public void testWildcardsInLookup() throws Exception {
    configure()
    assertNotNull(getLookup());
    type('*fz');
    assert !lookup
  }

  public void testSmartEnterWrapsConstructorCall() throws Throwable { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }
  public void testSmartEnterNoNewLine() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }
  public void testSmartEnterWithNewLine() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }

  public void testTabReplacesMethodNameWithLocalVariableName() throws Throwable { doTest('\t'); }
  public void testMethodParameterAnnotationClass() throws Throwable { doTest(); }
  public void testInnerAnnotation() { doTest('\n'); }
  public void testPrimitiveCastOverwrite() throws Throwable { doTest '\t' }
  public void testClassReferenceInFor() throws Throwable { doTest ' ' }
  public void testClassReferenceInFor2() throws Throwable { doTest ' ' }
  public void testClassReferenceInFor3() throws Throwable {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    doTest ' '
  }

  public void testEnumConstantFromEnumMember() throws Throwable { doTest(); }

  public void testPrimitiveMethodParameter() throws Throwable { doTest(); }

  public void testNewExpectedClassParens() throws Throwable { doTest('\n'); }

  public void testQualifyInnerMembers() throws Throwable { doTest('\n') }

  public void testDeepInner() throws Throwable {
    configure()
    assert myFixture.lookupElementStrings == ['ClassInner1', 'ClassInner1.ClassInner2']
    selectItem(lookup.items[1])
    checkResult()
  }

  public void testSuggestExpectedTypeMembers() throws Throwable { doTest('\n') }
  public void testSuggestExpectedTypeMembersInCall() throws Throwable { doTest('\n') }
  public void testSuggestExpectedTypeMembersInAnno() throws Throwable { doTest('\n') }
  public void testExpectedTypesDotSelectsItem() throws Throwable { doTest('.') }

  public void testExpectedTypeMembersVersusStaticImports() throws Throwable {
    configure()
    assertStringItems('XFOO', 'XFOX')
  }

  public void testDoubleExpectedTypeFactoryMethod() throws Throwable {
    configure()
    assertStringItems('Key', 'create', 'create')
    assert renderElement(myItems[1]).itemText == 'Key.<Boolean>create'
    assert renderElement(myItems[2]).itemText == 'Key.create'
  }

  public void testSuggestExpectedTypeMembersNonImported() throws Throwable {
    myFixture.addClass("package foo; public class Super { public static final Super FOO = null; }")
    myFixture.addClass("package foo; public class Usage { public static void foo(Super s) {} }")
    doTest('\n')
  }

  public void testStaticallyImportedInner() throws Throwable {
    configure()
    assertStringItems('AIOInner', 'ArrayIndexOutOfBoundsException')
  }

  public void testClassNameInIfBeforeIdentifier() throws Throwable {
    myFixture.addClass("public class ABCDEFFFFF {}")
    doTest('\n')
  }

  public void testClassNameWithInnersTab() throws Throwable { doTest('\t') }

  public void testClassNameWithGenericsTab() throws Throwable {doTest('\t') }
  public void testClassNameWithGenericsTab2() throws Throwable {doTest('\t') }

  public void testLiveTemplatePrefixTab() throws Throwable {doTest('\t') }

  public void testOnlyAnnotationsAfterAt() throws Throwable { doTest() }
  public void testOnlyAnnotationsAfterAt2() throws Throwable { doTest('\n') }

  public void testOnlyExceptionsInCatch1() throws Exception { doTest('\n') }
  public void testOnlyExceptionsInCatch2() throws Exception { doTest('\n') }
  public void testOnlyExceptionsInCatch3() throws Exception { doTest('\n') }
  public void testOnlyExceptionsInCatch4() throws Exception { doTest('\n') }

  public void testCommaAfterVariable() throws Throwable { doTest(',') }

  public void testClassAngleBracket() throws Throwable { doTest('<') }
  public void testNoArgsMethodSpace() throws Throwable { doTest(' ') }

  public void testClassSquareBracket() throws Throwable { doTest('[') }
  public void testPrimitiveSquareBracket() throws Throwable { doTest('[') }
  public void testVariableSquareBracket() throws Throwable { doTest('[') }
  public void testMethodSquareBracket() throws Throwable { doTest('[') }

  public void testMethodParameterTypeDot() throws Throwable { doAntiTest() }
  public void testNewGenericClass() throws Throwable { doTest('\n') }
  public void testNewGenericInterface() throws Throwable { doTest() }
  public void testEnumPrivateFinal() throws Throwable { doTest() }
  public void testNoFieldsInImplements() throws Throwable { doTest() }

  public void testSwitchConstantsFromReferencedClass() throws Throwable { doTest('\n') }

  public void testUnfinishedMethodTypeParameter() throws Throwable {
    configure()
    assertStringItems("MyParameter", "MySecondParameter")
  }
  public void testUnfinishedMethodTypeParameter2() throws Throwable {
    configure()
    assertStringItems("MyParameter", "MySecondParameter")
  }

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
    assertFirstStringItems("equals", "equalsIgnoreCase");
    complete()
    assertFirstStringItems("equals", "equalsIgnoreCase");
    type '('
    checkResult()
  }

  public void testClassNameInsideIdentifierInIf() throws Throwable {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    type '\n'
    checkResult()
  }

  public void testKeywordSmartEnter() {
    configure()
    myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT)
    checkResult()
  }

  public void testImportStringValue() throws Throwable {
    myFixture.addClass("package foo; public class StringValue {}")
    myFixture.addClass("package java.lang; class StringValue {}")
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    type ' '
    checkResult()
  }

  public void testPrimitiveArrayWithRBrace() throws Throwable { doTest '[' }

  public void testSuggestMembersOfStaticallyImportedClasses() throws Exception {
    myFixture.addClass("""package foo;
    public class Foo {
      public static void foo() {}
      public static void bar() {}
    }
    """)
    doTest('\n')
  }

  public void testSuggestMembersOfStaticallyImportedClassesUnqualifiedOnly() throws Exception {
    myFixture.addClass("""package foo;
    public class Foo {
      public static void foo() {}
      public static void bar() {}
    }
    """)
    configure()
    complete()
    assertOneElement(myFixture.getLookupElements())
    myFixture.type '\t'
    checkResult()
  }

  public void testInstanceMagicMethod() throws Exception { doTest() }

  public void testNoDotOverwrite() throws Exception { doTest('.') }

  public void testStaticInnerExtendingOuter() throws Exception { doTest() }
  public void testPrimitiveClass() throws Exception { doTest() }
  public void testPrimitiveArrayClass() throws Exception { doTest() }
  public void testPrimitiveArrayOnlyClass() throws Exception { doAntiTest() }
  public void testPrimitiveArrayInAnno() throws Exception { doTest() }

  public void testNewClassAngleBracket() throws Exception { doTest('<') }
  public void testNewClassAngleBracketExpected() throws Exception { doTest('<') }
  public void testNewClassSquareBracket() throws Exception { doTest('[') }

  public void testMethodColon() throws Exception { doTest(':') }
  public void testVariableColon() throws Exception { doTest(':') }

  public void testFinishByClosingParenthesis() throws Exception { doTest(')') }

  public void testNoMethodsInParameterType() {
    configure()
    assertFirstStringItems "final", "float"
  }

  public void testNonImportedClassInAnnotation() {
    myFixture.addClass("package foo; public class XInternalTimerServiceController {}")
    myFixture.configureByText "a.java", """
class XInternalError {}

@Anno(XInternal<caret>)
"""
    myFixture.complete(CompletionType.BASIC, 2)
    assertFirstStringItems "XInternalError", "XInternalTimerServiceController"
  }

  public void testMetaAnnotation() {
    myFixture.configureByText "a.java", "@<caret> @interface Anno {}"
    myFixture.complete(CompletionType.BASIC)
    assert myFixture.lookup.items.find { it.lookupString == 'Retention' }
  }

  public void testAnnotationClassFromWithinAnnotation() { doTest() }

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
  public void testStaticallyImportedFieldsTwiceSwitch() { doTest() }

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
    myFixture.type '*;'
    myFixture.checkResult "import java.lang.*;<caret>"
  }

  public void testDontPreselectCaseInsensitivePrefixMatch() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.configureByText "a.java", "import java.io.*; class Foo {{ int fileSize; fil<caret>x }}"
    myFixture.completeBasic()
    assert lookup.currentItem.lookupString == 'fileSize'
    myFixture.type('e')

    assert lookup.items[0].lookupString == 'File'
    assert lookup.items[1].lookupString == 'fileSize'
    assert lookup.currentItem == lookup.items[1]
  }

  public void testNoGenericsWhenChoosingWithParen() { doTest('Ma(') }

  public void testNoClosingWhenChoosingWithParenBeforeIdentifier() { doTest '(' }

  public void testPackageInMemberType() { doTest() }

  public void testConstantInAnno() { doTest() }

  public void testCharsetName() {
    myFixture.addClass("package java.nio.charset; public class Charset { public static Charset forName(String s) {} }")
    configureByTestName()
    assert myFixture.lookupElementStrings.contains('UTF-8')
  }

  public void testInnerClassInExtendsGenerics() {
    def text = "package bar; class Foo extends List<Inne<caret>> { public static class Inner {} }"
    myFixture.configureFromExistingVirtualFile(myFixture.addClass(text).containingFile.virtualFile)
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult(text.replace('Inne<caret>', 'Foo.Inner<caret>'))
  }

  public void testClassNameDot() { doTest('.') }

  public void testClassNameDotBeforeCall() {
    myFixture.addClass("package foo; public class FileInputStreamSmth {}")
    myFixture.configureByFile(getTestName(false) + ".java")
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    type '\b'
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    myFixture.complete(CompletionType.BASIC, 2)
    assert lookup
    type '.'
    checkResult()
  }

  public void testNoReturnAfterDot() {
    configure()
    assert !('return' in myFixture.lookupElementStrings)
  }

  public void testDuplicateExpectedTypeInTypeArgumentList() {
    configure()
    def items = myFixture.lookupElements.findAll { it.lookupString == 'String' }
    assert items.size() == 1
    assert LookupElementPresentation.renderElement(items[0]).tailText == ' (java.lang)'
  }

  public void testDuplicateInnerClass() {
    configure()
    def items = myFixture.lookupElements.findAll { it.lookupString == 'Inner' }
    assert items.size() == 1
  }

  public void testSameSignature() {
    configure()
    myFixture.assertPreferredCompletionItems(0, 's', 's, file', 's, file, a')
    lookup.setCurrentItem(lookup.items[2])
    myFixture.type('\n')
    checkResult()
  }

  public void testNoParenthesesAroundCallQualifier() { doTest() }

  public void testAllAssertClassesMethods() {
    myFixture.addClass 'package foo; public class Assert { public static boolean foo() {} }'
    myFixture.addClass 'package bar; public class Assert { public static boolean bar() {} }'
    configure()
    assert myFixture.lookupElementStrings == ['Assert.bar', 'Assert.foo']
    myFixture.type '\n'
    checkResult()
  }

  public void testCastVisually() {
    configure()
    def p = LookupElementPresentation.renderElement(myFixture.lookupElements[0])
    assert p.itemText == 'getValue'
    assert p.itemTextBold
    assert p.typeText == 'Foo'
  }

  public void testSuggestEmptySet() {
    configure()
    assert 'emptySet' == myFixture.lookupElementStrings[0]
    type '\n'
    checkResult()
  }

  public void testSuggestAllTypeArguments() {
    configure()
    assert 'String, String' == lookup.items[1].lookupString
    lookup.currentItem = lookup.items[1]
    type '\n'
    checkResult()
  }

  public void testMakeMultipleArgumentsFinalWhenInInner() {
    configure()
    def item = lookup.items.find { 'a, b' == it.lookupString }
    assert item
    lookup.currentItem = item
    type '\n'
    checkResult()
  }

  public void testNoFinalInAnonymousConstructor() { doTest() }
  public void testListArrayListCast() { doTest('\n') }
  public void testInterfaceImplementationNoCast() { doTest() }
  public void testStaticallyImportedMethodsBeforeExpression() { doTest() }
  public void testInnerChainedReturnType() { doTest() }
  public void testOverwriteGenericsAfterNew() { doTest('\n') }

  private CommonCodeStyleSettings getCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  public void testCompatibleInterfacesCast() {
    configure()
    assert myFixture.lookupElementStrings.containsAll(['foo', 'bar'])
  }

  public void testDontAutoInsertMiddleMatch() {
    configure()
    checkResult()
    assert lookup.items.size() == 1
  }

  public void testImplementViaCompletion() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'private', 'protected', 'public', 'public void run'
    def item = lookup.items[3]

    def p = LookupElementPresentation.renderElement(item)
    assert p.itemText == 'public void run'
    assert p.tailText == '(t, myInt) {...}'
    assert p.typeText == 'Foo'

    lookup.currentItem = item
    myFixture.type('\n')
    checkResult()
  }

  public void testImplementViaOverrideCompletion() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'Override', 'public void run'
    lookup.currentItem = lookup.items[1]
    myFixture.type('\n')
    checkResult()
  }

  public void testAccessorViaCompletion() {
    configure()

    def getter = myFixture.lookupElements.find { it.lookupString == 'public int getField' }
    def setter = myFixture.lookupElements.find { it.lookupString == 'public void setField' }
    assert getter : myFixture.lookupElementStrings
    assert setter : myFixture.lookupElementStrings

    def p = LookupElementPresentation.renderElement(getter)
    assert p.itemText == getter.lookupString
    assert p.tailText == '() {...}'
    assert !p.typeText

    p = LookupElementPresentation.renderElement(setter)
    assert p.itemText == setter.lookupString
    assert p.tailText == '(field) {...}'
    assert !p.typeText

    lookup.currentItem = getter
    myFixture.type('\n')
    checkResult()
  }

  public void testBraceOnNextLine() {
    codeStyleSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTest()
  }

  public void testDoForceBraces() {
    codeStyleSettings.DOWHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    doTest('\n')
  }

  public void testMulticaretSingleItemInsertion() {
    doTest()
  }

  public void testMulticaretMethodWithParen() {
    doTest()
  }

  public void testMulticaretTyping() {
    configure()
    assert lookup
    type('p')
    assert lookup
    type('\n')
    checkResult()
  }

  public void testMulticaretCompletionFromNonPrimaryCaret() {
    configure()
    myFixture.assertPreferredCompletionItems(0, "arraycopy")
  }

  public void testMulticaretCompletionFromNonPrimaryCaretWithTab() {
    doTest '\t'
  }

  public void "test complete lowercase class name"() {
    myFixture.addClass("package foo; public class myClass {}")
    myFixture.configureByText "a.java", """
class Foo extends my<caret>
"""
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.checkResult '''import foo.myClass;

class Foo extends myClass
'''
  }

  public void "test don't show static inner class after instance qualifier"() {
    myFixture.configureByText "a.java", """
class Foo {
  static class Inner {}
}
class Bar {
  void foo(Foo f) {
    f.<caret>
  }
}  
"""
    myFixture.completeBasic()
    assert !('Inner' in myFixture.lookupElementStrings)
  }

  public void "test show static member after instance qualifier when nothing matches"() {
    myFixture.configureByText "a.java", "class Foo{{ \"\".<caret> }}"
    myFixture.completeBasic()
    assert !('valueOf' in myFixture.lookupElementStrings)
    ((LookupImpl)myFixture.lookup).hide()
    myFixture.type 'val'
    myFixture.completeBasic()
    assert ('valueOf' in myFixture.lookupElementStrings)
  }

  public void testNoMathTargetMethods() { doAntiTest() }

  public void testNoLowercaseClasses() {
    myFixture.addClass("package foo; public class abcdefgXxx {}")
    doAntiTest()
    myFixture.complete(CompletionType.BASIC, 2)
    assertStringItems('abcdefgXxx')
  }

  public void testProtectedFieldInAnotherPackage() {
    myFixture.addClass("package foo; public class Super { protected String myString; }");
    doTest()
  }

  public void testUnimportedStaticInnerClass() {
    myFixture.addClass("package foo; public class Super { public static class Inner {} }");
    doTest()
  }

  public void testNoJavaLangPackagesInImport() { doAntiTest() }

  public void testNoStaticDuplicatesFromExpectedMemberFactories() {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.assertPreferredCompletionItems(0, "xcreateZoo", "xcreateElephant");
  }

  public void testNoInaccessibleCompiledElements() {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    checkResultByFile(getTestName(false) + ".java");
    assertEmpty(myItems);
    assertNull(getLookup());
  }

  public void "test code cleanup during completion generation"() {
    myFixture.configureByText "a.java", "class Foo {int i; ge<caret>}"
    myFixture.enableInspections(new UnqualifiedFieldAccessInspection())
    myFixture.complete(CompletionType.BASIC)
    myFixture.checkResult '''class Foo {int i;

    public int getI() {
        return this.i;
    }
}'''
  }
  
  public void testIndentingForSwitchCase() { doTest() }

  public void testIncrementalCopyReparse() {
    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).disableBackgroundCommit(testRootDisposable)
    
    myFixture.configureByText('a.java', 'class Fooxxxxxxxxxx { Fooxxxxx<caret>a f;\n' + 'public void foo() {}\n' * 10000 + '}')
    def items = myFixture.completeBasic()
    PsiClass c1 = items[0].object
    assert !c1.physical
    assert CompletionUtil.getOriginalElement(c1)
    
    getLookup().hide()
    myFixture.type('x')
    items = myFixture.completeBasic()
    PsiClass c2 = items[0].object
    assert !c2.physical
    assert CompletionUtil.getOriginalElement(c2)

    assert c1.is(c2)
  }
}
