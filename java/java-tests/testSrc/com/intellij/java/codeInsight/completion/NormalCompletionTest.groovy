/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.completion
import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
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
import com.intellij.util.ui.UIUtil
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection

class NormalCompletionTest extends LightFixtureCompletionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/normal/"
  }

  void testSimple() throws Exception {
    configureByFile("Simple.java")
    assertStringItems("_local1", "_local2", "_field", "_baseField", "_method", "_baseMethod")
  }

  void testCastToPrimitive1() throws Exception {
    configureByFile("CastToPrimitive1.java")

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return
    }
    assertTrue(false)
  }

  void testCastToPrimitive2() throws Exception {
    configureByFile("CastToPrimitive2.java")

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return
    }
    assertTrue(false)
  }

  void testCastToPrimitive3() throws Exception {
    configureByFile("CastToPrimitive3.java")

    for (final LookupElement item : myItems) {
      if (item.getLookupString().equals("int")) return
    }
    assertTrue(false)
  }

  void testWriteInInvokeLater() throws Exception {
    configureByFile("WriteInInvokeLater.java")
  }

  void testQualifiedNew1() throws Exception {
    configure()
    assertStringItems "IInner", "Inner"
  }

  void testQualifiedNew2() throws Exception {
    configure()
    assertStringItems "AnInner", "Inner"
  }

  void testKeywordsInName() throws Exception {
    doTest 'a\n'
  }

  void testSimpleVariable() throws Exception { doTest('\n') }

  void testTypeParameterItemPresentation() {
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

  void testDisplayDefaultValueInAnnotationMethods() {
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

  void testMethodItemPresentation() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "equals" == presentation.itemText
    assert "(Object anObject)" == presentation.tailText
    assert "boolean" == presentation.typeText

    assert !presentation.tailGrayed
    assert presentation.itemTextBold
  }

  private static LookupElementPresentation renderElement(LookupElement element) {
    return LookupElementPresentation.renderElement(element)
  }

  void testFieldItemPresentationGenerics() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "target" == presentation.itemText
    assert !presentation.tailText
    assert "String" == presentation.typeText
  }

  void testMethodItemPresentationGenerics() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[1])
    assert "add" == presentation.itemText
    assert "(int index, String element)" == presentation.tailText
    assert "void" == presentation.typeText

    presentation = renderElement(myItems[0])
    assert "(String e)" == presentation.tailText
    assert "boolean" == presentation.typeText

    assert !presentation.tailGrayed
    assert presentation.itemTextBold
  }

  void testPreferLongerNamesOption() throws Exception {
    configureByFile("PreferLongerNamesOption.java")

    assertEquals(3, myItems.length)
    assertEquals("abcdEfghIjk", myItems[0].getLookupString())
    assertEquals("efghIjk", myItems[1].getLookupString())
    assertEquals("ijk", myItems[2].getLookupString())

    LookupManager.getInstance(getProject()).hideActiveLookup()

    CodeStyleSettingsManager.getSettings(getProject()).PREFER_LONGER_NAMES = false
    try{
      configureByFile("PreferLongerNamesOption.java")

      assertEquals(3, myItems.length)
      assertEquals("ijk", myItems[0].getLookupString())
      assertEquals("efghIjk", myItems[1].getLookupString())
      assertEquals("abcdEfghIjk", myItems[2].getLookupString())
    }
    finally{
      CodeStyleSettingsManager.getSettings(getProject()).PREFER_LONGER_NAMES = true
    }
  }

  void testSCR7208() throws Exception {
    configureByFile("SCR7208.java")
  }

  void testProtectedFromSuper() throws Exception {
    configureByFile("ProtectedFromSuper.java")
    Arrays.sort(myItems)
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "xxx") > 0)
  }

  void testBeforeInitialization() throws Exception {
    configureByFile("BeforeInitialization.java")
    assertNotNull(myItems)
    assertTrue(myItems.length > 0)
  }

  void testProtectedFromSuper2() throws Exception {

    configureByFile("ProtectedFromSuper.java")
    Arrays.sort(myItems)
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "xxx") > 0)
  }

  void testClassLiteralInArrayAnnoInitializer() throws Throwable { doTest() }

  void testClassLiteralInArrayAnnoInitializer2() throws Throwable { doTest() }

  void testReferenceParameters() throws Exception {
    configureByFile("ReferenceParameters.java")
    assertNotNull(myItems)
    myFixture.assertPreferredCompletionItems 0, 'AAAA', 'AAAB'
  }

  @Override
  protected void tearDown() throws Exception {
    CodeInsightSettings.instance.AUTOCOMPLETE_ON_CODE_COMPLETION = true
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = true
    super.tearDown()
  }

  void testConstructorName1() throws Exception {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    configure()
    assert 'ABCDE' in myFixture.lookupElementStrings
  }

  void testConstructorName2() throws Exception {
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    configure()
    assert 'ABCDE' in myFixture.lookupElementStrings
  }

  void testObjectsInThrowsBlock() throws Exception {
    configureByFile("InThrowsCompletion.java")
    assert "C" == myFixture.lookupElementStrings[0]
    assert "B" in myFixture.lookupElementStrings
  }

  void testAnnoParameterValue() throws Exception {
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

  void testAfterInstanceof() throws Exception {
    configureByFile("AfterInstanceof.java")
    assert "A" in myFixture.lookupElementStrings
  }

  void testAfterCast1() throws Exception {
    configureByFile("AfterCast1.java")

    assertNotNull(myItems)
    assertEquals(2, myItems.length)
  }

  void testAfterCast2() throws Exception {
    configureByFile("AfterCast2.java")
    checkResultByFile("AfterCast2-result.java")
  }

  void testMethodCallForTwoLevelSelection() throws Exception {
    configureByFile("MethodLookup.java")
    assertEquals(2, myItems.length)
  }

  void testMethodCallBeforeAnotherStatementWithParen() throws Exception {
    configureByFile("MethodLookup2.java")
    checkResultByFile("MethodLookup2_After.java")
  }

  void testMethodCallBeforeAnotherStatementWithParen2() throws Exception {
    codeStyleSettings.METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE = true
    configureByFile("MethodLookup2.java")
    checkResultByFile("MethodLookup2_After2.java")
  }

  void testSwitchEnumLabel() throws Exception {
    configureByFile("SwitchEnumLabel.java")
    assertEquals(3, myItems.length)
  }

  void testSwitchCaseWithEnumConstant() { doTest() }

  void testSecondSwitchCaseWithEnumConstant() { doTest() }

  void testInsideSwitchCaseWithEnumConstant() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'compareTo', 'equals'
  }

  void testMethodInAnnotation() throws Exception {
    configureByFile("Annotation.java")
    checkResultByFile("Annotation_after.java")
  }

  void testMethodInAnnotation2() throws Exception {
    configureByFile("Annotation2.java")
    checkResultByFile("Annotation2_after.java")
  }

  void testMethodInAnnotation3() throws Exception {
    configureByFile("Annotation3.java")
    checkResultByFile("Annotation3_after.java")
  }

  void testMethodInAnnotation5() throws Exception {
    configureByFile("Annotation5.java")
    checkResultByFile("Annotation5_after.java")
  }

  void testMethodInAnnotation7() throws Exception {
    configureByFile("Annotation7.java")
    selectItem(myItems[0])
    checkResultByFile("Annotation7_after.java")
  }

  void testEnumInAnnotation() throws Exception {
    configureByFile("Annotation4.java")
    checkResultByFile("Annotation4_after.java")
  }

  void testSecondAttribute() throws Exception {
    configureByFile("Annotation6.java")
    checkResultByFile("Annotation6_after.java")
  }

  void testIDEADEV6408() throws Exception {
    configureByFile("IDEADEV6408.java")
    assertFirstStringItems "boolean", "byte"
  }

  void testMethodWithLeftParTailType() throws Exception {
    configureByFile("MethodWithLeftParTailType.java")
    type('(')
    checkResultByFile("MethodWithLeftParTailType_after.java")

    configureByFile("MethodWithLeftParTailType2.java")
    type('(')
    checkResultByFile("MethodWithLeftParTailType2_after.java")
  }

  void testSuperErasure() throws Exception {
    configureByFile("SuperErasure.java")
    checkResultByFile("SuperErasure_after.java")
  }

  void testMethodWithLeftParTailTypeNoPairBrace() throws Exception {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false
    doTest('(')
  }

  void testMethodWithLeftParTailTypeNoPairBrace2() throws Exception {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false

    //no tail type should work the normal way
    configureByFile("MethodWithLeftParTailTypeNoPairBrace.java")
    selectItem(myItems[0])
    checkResultByFile("MethodWithLeftParTailTypeNoPairBrace_after2.java")
  }

  void testMethodNoPairBrace() throws Exception {
    CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET = false
    doTest '\n'
  }

  void testExcessSpaceInTypeCast() throws Throwable {
   configure()
   selectItem(myItems[0])
   checkResult()
  }

  void testFieldType() { doTest() }

  void testPackageInAnnoParam() throws Throwable {
    doTest()
  }

  void testAnonymousTypeParameter() throws Throwable { doTest() }

  void testClassLiteralInAnnoParam() throws Throwable {
    doTest()
  }

  void testNoForceBraces() {
    codeStyleSettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    doTest('\n')
  }

  void testExcludeStringBuffer() throws Throwable {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, StringBuffer.name)
    doAntiTest()
  }

  void testExcludeInstanceInnerClasses() throws Throwable {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "foo")
    myFixture.addClass 'package foo; public class Outer { public class Inner {} }'
    myFixture.addClass 'package bar; public class Inner {}'
    configure()
    assert 'bar.Inner' == ((JavaPsiClassReferenceElement)myFixture.lookupElements[0]).qualifiedName
    assert myFixture.lookupElementStrings == ['Inner']
  }

  void testExcludedInstanceInnerClassCreation() throws Throwable {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "foo")
    myFixture.addClass 'package foo; public class Outer { public class Inner {} }'
    myFixture.addClass 'package bar; public class Inner {}'
    configure()
    assert 'foo.Outer.Inner' == ((JavaPsiClassReferenceElement)myFixture.lookupElements[0]).qualifiedName
    assert myFixture.lookupElementStrings == ['Inner']
  }

  void testExcludedInstanceInnerClassQualifiedReference() throws Throwable {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "foo")
    myFixture.addClass 'package foo; public class Outer { public class Inner {} }'
    myFixture.addClass 'package bar; public class Inner {}'
    configure()
    assert 'foo.Outer.Inner' == ((JavaPsiClassReferenceElement)myFixture.lookupElements[0]).qualifiedName
    assert myFixture.lookupElementStrings == ['Inner']
  }

  void testStaticMethodOfExcludedClass() {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "foo")
    myFixture.addClass 'package foo; public class Outer { public static void method() {} }'
    configure()
    assert myFixture.lookupElementStrings == ['method']
  }

  void testExcludeWildcards() {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "foo.Outer.*1*")
    myFixture.addClass '''
package foo; 
public class Outer { 
  public static void method1() {} 
  public static void method2() {} 
  public static void method12() {} 
  public static void method42() {} 
}'''
    myFixture.configureByText 'a.java', 'class C {{ foo.Outer.m<caret> }}'
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['method2', 'method42']
  }

  void testAtUnderClass() throws Throwable {
    doTest('\n')
  }

  void testLocalClassName() throws Throwable { doTest() }

  void testAssigningFieldForTheFirstTime() throws Throwable { doTest() }

  void testClassTypeParameters() throws Throwable {
    configure()
    assert 'K' in myFixture.lookupElementStrings
  }

  void testClassTypeParametersGenericBounds() throws Throwable {
    configure()
    assert 'K' in myFixture.lookupElementStrings
  }

  void testLocalClassTwice() throws Throwable {
    configure()
    assertOrderedEquals myFixture.lookupElementStrings, 'Zoooz', 'Zooooo'
  }

  void testLocalTopLevelConflict() throws Throwable {
    configure()
    assertOrderedEquals myFixture.lookupElementStrings, 'Zoooz', 'Zooooo'
  }

  void testFinalBeforeMethodCall() throws Throwable {
    configure()
    assertStringItems 'final', 'finalize'
  }

  void testMethodCallAfterFinally() { doTest() }

  void testPrivateInAnonymous() throws Throwable { doTest() }

  void testStaticMethodFromOuterClass() {
    configure()
    assertStringItems 'foo', 'A.foo', 'for'
    assert LookupElementPresentation.renderElement(myItems[1]).itemText == 'A.foo'
    selectItem(myItems[1])
    checkResult()
  }

  void testInstanceMethodFromOuterClass() {
    configure()
    assertStringItems 'foo', 'A.this.foo', 'for'
    assert LookupElementPresentation.renderElement(myItems[1]).itemText == 'A.this.foo'
    selectItem(myItems[1])
    checkResult()
  }

  void testMethodParenthesesSpaces() throws Throwable {
    codeStyleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    doTest()
  }

  void testMethodParenthesesSpacesArgs() throws Throwable {
    codeStyleSettings.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
    codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    doTest()
  }

  void testAtUnderClassNoModifiers() throws Throwable {
    doTest()
  }

  void testBreakInIfCondition() throws Throwable { doTest() }

  void testAccessStaticViaInstance() throws Throwable { doTest() }

  void testIfConditionLt() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'getAnnotationsAreaOffset'
  }

  void testAccessStaticViaInstanceSecond() throws Throwable {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    checkResult()
  }

  void testAccessInstanceFromStaticSecond() throws Throwable {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    checkResult()
  }

  void testContinueLabel() throws Throwable { doTest() }

  void testAnonymousProcess() {
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

  void testNoThisInComment() throws Throwable { doAntiTest() }

  void testIncNull() throws Throwable {
    configure()
    checkResultByFile(getTestName(false) + ".java")
    assert !('null' in myFixture.lookupElementStrings)
  }

  void testLastExpressionInFor() throws Throwable { doTest() }

  void testOnlyKeywordsInsideSwitch() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    assertStringItems("case", "default")
  }

  void testBooleanLiterals() throws Throwable {
    doTest('\n')
  }

  void testDoubleBooleanInParameter() throws Throwable {
    configure()
    assertFirstStringItems("boolean", "byte")
  }

  void testDoubleConstant() throws Throwable {
    configure()
    assertStringItems("XFOO")
  }

  void testNotOnlyKeywordsInsideSwitch() throws Throwable {
    doTest()
  }

  void testChainedCallOnNextLine() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    selectItem(myItems[0])
    checkResult()
  }

  void testFinishWithDot() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    type('.')
    checkResult()
  }

  void testEnclosingThis() throws Throwable { doTest() }

  void testSeamlessConstant() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    selectItem(myItems[0])
    checkResult()
  }

  void testDefaultAnnoParam() throws Throwable { doTest() }

  void testSpaceAfterLookupString() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    type(' ')
    assertNull(getLookup())
    checkResult()
  }

  void testNoSpaceInParensWithoutParams() throws Throwable {
    codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    try {
      doTest()
    }
    finally {
      codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = false
    }
  }

  void testTwoSpacesInParensWithParams() throws Throwable {
    codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    doTest()
  }

  void testQualifierAsPackage() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    selectItem(myItems[0])
    checkResult()
  }

  void testQualifierAsPackage2() throws Throwable {
    doTest()
  }

  void testQualifierAsPackage3() throws Throwable {
    doTest()
  }

  void testPreselectEditorSelection() {
    configure()
    assert lookup.currentItem != myFixture.lookupElements[0]
    assert 'finalize' == lookup.currentItem.lookupString
  }

  void testNoMethodsInNonStaticImports() {
    configure()
    assertStringItems("*")
  }

  void testMembersInStaticImports() { doTest() }

  void testPackageNamedVariableBeforeAssignment() throws Throwable {
    doTest()
  }

  void testInnerEnumConstant() throws Throwable { doTest('\n') }

  void testNoExpectedReturnTypeDuplication() {
    configure()
    assert myFixture.lookupElementStrings == ['boolean', 'byte']
  }
  void testNoExpectedVoidReturnTypeDuplication() {
    configure()
    assert myFixture.lookupElementStrings == ['void']
  }

  void testNoExpectedArrayTypeDuplication() {
    configure()
    assert myFixture.lookupElementStrings == ['char']
  }

  void testMethodReturnType() throws Throwable {
    doTest()
  }

  void testMethodReturnTypeNoSpace() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    selectItem(myItems[0])
    checkResult()
  }

  void testEnumWithoutConstants() throws Throwable {
    doTest()
  }

  void testDoWhileMethodCall() throws Throwable {
    doTest()
  }

  void testSecondTypeParameterExtends() throws Throwable {
    doTest()
  }

  void testGetterWithExistingNonEmptyParameterList() throws Throwable {
    doTest()
  }

  void testNothingAfterNumericLiteral() throws Throwable { doAntiTest() }

  void testNothingAfterTypeParameterQualifier() { doAntiTest() }

  void testExcludeVariableBeingDeclared() { doAntiTest() }

  void testExcludeVariableBeingDeclared2() { doAntiTest() }

  void testSpacesAroundEq() throws Throwable { doTest('=') }

  void _testClassBeforeCast() throws Throwable { doTest '\n' }

  void testNoAllClassesOnQualifiedReference() throws Throwable {
    doAntiTest()
  }

  void testFinishClassNameWithDot() throws Throwable {
    doTest('.')
  }

  void testFinishClassNameWithLParen() throws Throwable {
    doTest('(')
  }

  void testSelectNoParameterSignature() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    final int parametersCount = ((PsiMethod)getLookup().getCurrentItem().getObject()).getParameterList().getParametersCount()
    assertEquals(0, parametersCount)
    type '\n'
    checkResult()
  }

  void testCompletionInsideClassLiteral() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    type('\n')
    checkResult()
  }

  void testFieldNegation() throws Throwable { doTest('!') }

  void testDefaultInSwitch() throws Throwable { doTest() }

  void testBreakInSwitch() throws Throwable { doTest() }

  void testSuperInConstructor() throws Throwable {
    doTest()
  }

  void testSuperInConstructorWithParams() throws Throwable {
    doTest()
  }

  void testSuperInMethod() throws Throwable {
    doTest()
  }

  void testSecondMethodParameterName() throws Throwable {
    doTest()
  }

  void testAnnotationAsUsualObject() throws Throwable {
    doTest()
  }

  void testAnnotationAsUsualObjectFromJavadoc() throws Throwable {
    doTest()
  }

  void testAnnotationAsUsualObjectInsideClass() throws Throwable {
    doTest()
  }

  void testAnnotationOnNothingParens() throws Throwable {
    doTest()
  }

  void testMultiResolveQualifier() throws Throwable {
    doTest()
  }

  void testSecondMethodParameter() throws Throwable { doTest() }

  void testReturnInCase() throws Throwable { doTest() }

  void testUnboxedConstantsInCase() throws Throwable { doTest() }

  void testAnnotationWithoutValueMethod() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    assertStringItems("bar", "foo")
  }

  void testAddExplicitValueInAnnotation() throws Throwable {
    configureByTestName()
    assertStringItems("bar", "goo")
    selectItem(myItems[0])
    checkResult()
  }

  void testUnnecessaryMethodMerging() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    assertStringItems("fofoo", "fofoo")
  }

  void testMethodMergingMinimalTail() { doTest() }

  void testAnnotationQualifiedName() throws Throwable {
    doTest()
  }

  void testClassNameGenerics() throws Throwable {
    doTest('\n')
  }

  void testClassNameAnonymous() throws Throwable {
    doTest('\n')
  }

  void testClassNameWithInner() throws Throwable {
    configure()
    assertStringItems 'Zzoo', 'Zzoo.Impl'
    type '\n'
    checkResult()
  }

  void testClassNameWithInner2() throws Throwable { doTest('\n') }

  void testClassNameWithInstanceInner() throws Throwable { doTest('\n') }

  void testDoubleFalse() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    assertFirstStringItems("false", "fefefef", "float", "finalize")
  }

  void testSameNamedVariableInNestedClasses() throws Throwable {
    doTest()
  }

  void testHonorUnderscoreInPrefix() throws Throwable {
    doTest()
  }

  void testNoSemicolonAfterExistingParenthesesEspeciallyIfItsACast() throws Throwable { doTest() }

  void testReturningTypeVariable() throws Throwable { doTest() }

  void testReturningTypeVariable2() throws Throwable { doTest() }

  void testReturningTypeVariable3() throws Throwable { doTest() }

  void testImportInGenericType() throws Throwable {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.type('\n')
    checkResult()
  }

  void testCaseTailType() throws Throwable { doTest() }

  def doPrimitiveTypeTest() {
    configure()
    checkResultByFile(getTestName(false) + ".java")
    assertTrue 'boolean' in myFixture.lookupElementStrings
  }

  private def configure() {
    configureByTestName()
  }

  void testFinalInForLoop() throws Throwable {
    configure()
    assertStringItems 'final'
  }

  void testFinalInForLoop2() throws Throwable {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'finalize', 'final'
  }

  void testOnlyClassesInExtends() throws Throwable {
    configure()
    assertStringItems 'Inner'
  }

  void testNoThisClassInExtends() throws Throwable {
    configure()
    assertStringItems 'Fooxxxx2'
  }

  void testPrimitiveTypesInForLoop() throws Throwable { doPrimitiveTypeTest() }

  void testPrimitiveTypesInForLoop2() throws Throwable { doPrimitiveTypeTest() }

  void testPrimitiveTypesInForLoop3() throws Throwable { doPrimitiveTypeTest() }

  void testPrimitiveTypesInForLoop4() throws Throwable { doPrimitiveTypeTest() }

  void testPrimitiveTypesInForLoop5() throws Throwable { doPrimitiveTypeTest() }

  void testPrimitiveTypesInForLoop6() throws Throwable { doPrimitiveTypeTest() }

  void testPrimitiveTypesInForLoopSpace() throws Throwable {
    configure()
    myFixture.type ' '
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void testSuggestInaccessibleOnSecondInvocation() throws Throwable {
    configure()
    assertStringItems("_bar", "_goo")
    complete()
    assertStringItems("_bar", "_goo", "_foo")
    getLookup().setCurrentItem(getLookup().getItems().get(2))
    selectItem(lookup.items[2], Lookup.NORMAL_SELECT_CHAR)
    checkResult()
  }

  void testNoCommonPrefixInsideIdentifier() throws Throwable {
    final String path = getTestName(false) + ".java"
    configureByFile(path)
    checkResultByFile(path)
    assertStringItems("fai1", "fai2")
  }

  void testProtectedInaccessibleOnSecondInvocation() throws Throwable {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.type('\n')
    checkResult()
  }

  void testPropertyReferencePrefix() throws Throwable {
    myFixture.addFileToProject("test.properties", "foo.bar=Foo! Bar!").getVirtualFile()
    doAntiTest()
  }

  private void doTest() throws Exception {
    configure()
    checkResult()
  }

  private void doTest(String finishChar) throws Exception {
    configure()
    type finishChar
    checkResult()
  }

  void testSecondAnonymousClassParameter() { doTest() }

  void testSpaceAfterReturn() throws Throwable {
    configure()
    type '\n'
    checkResult()
  }

  private def checkResult() {
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void testIntersectionTypeMembers() throws Throwable {
    configure()
    assertStringItems "fooa", "foob"
  }

  void testNoReturnInTernary() throws Throwable { doTest() }

  void testWildcardsInLookup() throws Exception {
    configure()
    assertNotNull(getLookup())
    type('*fz')
    assert !lookup
  }

  void testSmartEnterWrapsConstructorCall() throws Throwable { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }

  void testSmartEnterNoNewLine() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }

  void testSmartEnterWithNewLine() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }

  void testSmartEnterGuessArgumentCount() throws Throwable { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }

  void testSmartEnterInsideArrayBrackets() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }

  void testTabReplacesMethodNameWithLocalVariableName() throws Throwable { doTest('\t') }

  void testMethodParameterAnnotationClass() throws Throwable { doTest() }

  void testInnerAnnotation() { doTest('\n') }

  void testPrimitiveCastOverwrite() throws Throwable { doTest() }

  void testClassReferenceInFor() throws Throwable { doTest ' ' }

  void testClassReferenceInFor2() throws Throwable { doTest ' ' }

  void testClassReferenceInFor3() throws Throwable {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    doTest ' '
  }

  void testEnumConstantFromEnumMember() throws Throwable { doTest() }

  void testPrimitiveMethodParameter() throws Throwable { doTest() }

  void testNewExpectedClassParens() throws Throwable { doTest('\n') }

  void testQualifyInnerMembers() throws Throwable { doTest('\n') }

  void testDeepInner() throws Throwable {
    configure()
    assert myFixture.lookupElementStrings == ['ClassInner1', 'ClassInner1.ClassInner2']
    selectItem(lookup.items[1])
    checkResult()
  }

  void testSuggestExpectedTypeMembers() throws Throwable { doTest('\n') }

  void testSuggestExpectedTypeMembersInCall() throws Throwable { doTest('\n') }

  void testSuggestExpectedTypeMembersInAnno() throws Throwable { doTest('\n') }

  void testExpectedTypesDotSelectsItem() throws Throwable { doTest('.') }

  void testExpectedTypeMembersVersusStaticImports() throws Throwable {
    configure()
    assertStringItems('XFOO', 'XFOX')
  }

  void testDoubleExpectedTypeFactoryMethod() throws Throwable {
    configure()
    assertStringItems('Key', 'create', 'create')
    assert renderElement(myItems[1]).itemText == 'Key.<Boolean>create'
    assert renderElement(myItems[2]).itemText == 'Key.create'
  }

  void testSuggestExpectedTypeMembersNonImported() throws Throwable {
    myFixture.addClass("package foo; public class Super { public static final Super FOO = null; }")
    myFixture.addClass("package foo; public class Usage { public static void foo(Super s) {} }")
    doTest('\n')
  }

  void testStaticallyImportedInner() throws Throwable {
    configure()
    assertStringItems('AIOInner', 'ArrayIndexOutOfBoundsException')
  }

  void testClassNameInIfBeforeIdentifier() throws Throwable {
    myFixture.addClass("public class ABCDEFFFFF {}")
    doTest('\n')
  }

  void testClassNameWithInnersTab() throws Throwable { doTest('\t') }

  void testClassNameWithGenericsTab() throws Throwable { doTest('\t') }

  void testClassNameWithGenericsTab2() throws Throwable { doTest('\t') }

  void testLiveTemplatePrefixTab() throws Throwable { doTest('\t') }

  void testOnlyAnnotationsAfterAt() throws Throwable { doTest() }

  void testOnlyAnnotationsAfterAt2() throws Throwable { doTest('\n') }

  void testAnnotationBeforeIdentifier() { doTest('\n') }

  void testAnnotationBeforeQualifiedReference() { doTest('\n') }

  void testAnnotationBeforeIdentifierFinishWithSpace() { doTest(' ') }

  void testOnlyExceptionsInCatch1() throws Exception { doTest('\n') }

  void testOnlyExceptionsInCatch2() throws Exception { doTest('\n') }

  void testOnlyExceptionsInCatch3() throws Exception { doTest('\n') }

  void testOnlyExceptionsInCatch4() throws Exception { doTest('\n') }

  void testCommaAfterVariable() throws Throwable { doTest(',') }

  void testClassAngleBracket() throws Throwable { doTest('<') }

  void testNoArgsMethodSpace() throws Throwable { doTest(' ') }

  void testClassSquareBracket() throws Throwable { doTest('[') }

  void testPrimitiveSquareBracket() throws Throwable { doTest('[') }

  void testVariableSquareBracket() throws Throwable { doTest('[') }

  void testMethodSquareBracket() throws Throwable { doTest('[') }

  void testMethodParameterTypeDot() throws Throwable { doAntiTest() }

  void testNewGenericClass() throws Throwable { doTest('\n') }

  void testNewGenericInterface() throws Throwable { doTest() }

  void testEnumPrivateFinal() throws Throwable { doTest() }

  void testNoFieldsInImplements() throws Throwable { doTest() }

  void testSwitchConstantsFromReferencedClass() throws Throwable { doTest('\n') }

  void testSwitchValueFinishWithColon() throws Throwable { doTest(':') }

  void testUnfinishedMethodTypeParameter() throws Throwable {
    configure()
    assertStringItems("MyParameter", "MySecondParameter")
  }

  void testUnfinishedMethodTypeParameter2() throws Throwable {
    configure()
    assertStringItems("MyParameter", "MySecondParameter")
  }

  void testSuperProtectedMethod() throws Throwable {
    myFixture.addClass """package foo;
      public class Bar {
          protected void foo() { }
      }"""
    doTest()
  }

  void testOuterSuperMethodCall() {
    configure()
    assert 'Class2.super.put' == LookupElementPresentation.renderElement(myItems[0]).itemText
    type '\n'
    checkResult() }

  void testTopLevelClassesFromPackaged() throws Throwable {
    myFixture.addClass "public class Fooooo {}"
    final text = "package foo; class Bar { Fooo<caret> }"
    def file = myFixture.addFileToProject("foo/Bar.java", text)
    myFixture.configureFromExistingVirtualFile file.virtualFile
    assertEmpty myFixture.completeBasic()
    myFixture.checkResult text
  }

  void testRightShift() throws Throwable {
    configure()
    assertStringItems("myField1", "myField2")
  }

  void testAfterCommonPrefix() throws Throwable {
    configure()
    type 'eq'
    assertFirstStringItems("equals", "equalsIgnoreCase")
    complete()
    assertFirstStringItems("equals", "equalsIgnoreCase")
    type '('
    checkResult()
  }

  void testClassNameInsideIdentifierInIf() throws Throwable {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    type '\n'
    checkResult()
  }

  void testKeywordSmartEnter() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'null', 'nullity'
    myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT)
    checkResult()
  }
  void testSynchronizedArgumentSmartEnter() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }

  void testImportStringValue() throws Throwable {
    myFixture.addClass("package foo; public class StringValue {}")
    myFixture.addClass("package java.lang; class StringValue {}")
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    type ' '
    checkResult()
  }

  void testPrimitiveArrayWithRBrace() throws Throwable { doTest '[' }

  void testSuggestMembersOfStaticallyImportedClasses() throws Exception {
    myFixture.addClass("""package foo;
    public class Foo {
      public static void foo() {}
      public static void bar() {}
    }
    """)
    doTest('\n')
  }

  void testSuggestMembersOfStaticallyImportedClassesUnqualifiedOnly() throws Exception {
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

  void testSuggestMembersOfStaticallyImportedClassesConflictWithLocalMethod() throws Exception {
    myFixture.addClass("""package foo;
    public class Foo {
      public static void foo() {}
      public static void bar() {}
    }
    """)
    configure()
    myFixture.assertPreferredCompletionItems 0, 'bar', 'bar'
    assert LookupElementPresentation.renderElement(myFixture.lookupElements[1]).itemText == 'Foo.bar'
    myFixture.lookup.currentItem = myFixture.lookupElements[1]
    myFixture.type '\t'
    checkResult()
  }

  void testSuggestMembersOfStaticallyImportedClassesConflictWithLocalField() throws Exception {
    myFixture.addClass("""package foo;
    public class Foo {
      public static int foo = 1;
      public static int bar = 2;
    }
    """)
    configure()
    myFixture.assertPreferredCompletionItems 0, 'bar', 'Foo.bar'
    myFixture.lookup.currentItem = myFixture.lookupElements[1]
    myFixture.type '\t'
    checkResult()
  }

  void testInstanceMagicMethod() throws Exception { doTest() }

  void testNoDotOverwrite() throws Exception { doTest('.') }

  void testStaticInnerExtendingOuter() throws Exception { doTest() }

  void testPrimitiveClass() throws Exception { doTest() }

  void testPrimitiveArrayClass() throws Exception { doTest() }

  void testPrimitiveArrayOnlyClass() throws Exception { doAntiTest() }

  void testPrimitiveArrayInAnno() throws Exception { doTest() }

  void testNewClassAngleBracket() throws Exception { doTest('<') }

  void testNewClassAngleBracketExpected() throws Exception { doTest('<') }

  void testNewClassSquareBracket() throws Exception { doTest('[') }

  void testMethodColon() throws Exception { doTest(':') }

  void testVariableColon() throws Exception { doTest(':') }

  void testFinishByClosingParenthesis() throws Exception { doTest(')') }

  void testNoMethodsInParameterType() {
    configure()
    assertFirstStringItems "final", "float"
  }

  void testNonImportedClassInAnnotation() {
    myFixture.addClass("package foo; public class XInternalTimerServiceController {}")
    myFixture.configureByText "a.java", """
class XInternalError {}

@Anno(XInternal<caret>)
"""
    myFixture.complete(CompletionType.BASIC, 2)
    assertFirstStringItems "XInternalError", "XInternalTimerServiceController"
  }

  void testNonImportedAnnotationClass() {
    myFixture.addClass("package foo; public @interface XAnotherAnno {}")
    configure()
    type('X')
    assertFirstStringItems "XAnno", "XAnotherAnno"
  }

  void testMetaAnnotation() {
    myFixture.configureByText "a.java", "@<caret> @interface Anno {}"
    myFixture.complete(CompletionType.BASIC)
    assert myFixture.lookup.items.find { it.lookupString == 'Retention' }
  }

  void testAnnotationClassFromWithinAnnotation() { doTest() }

  void testStaticallyImportedFieldsTwice() {
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

  void testStaticallyImportedFieldsTwiceSwitch() { doTest() }

  void testStatementKeywords() {
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

  void testExpressionKeywords() {
    myFixture.configureByText("a.java", """
      class Bar {{
        foo(<caret>xxx)
      }}
    """)
    myFixture.completeBasic()
    final def strings = myFixture.lookupElementStrings
    assertTrue 'new' in strings
  }

  void testImportAsterisk() {
    myFixture.configureByText "a.java", "import java.lang.<caret>"
    myFixture.completeBasic()
    myFixture.type '*;'
    myFixture.checkResult "import java.lang.*;<caret>"
  }

  void testDontPreselectCaseInsensitivePrefixMatch() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.configureByText "a.java", "import java.io.*; class Foo {{ int fileSize; fil<caret>x }}"
    myFixture.completeBasic()
    assert lookup.currentItem.lookupString == 'fileSize'
    myFixture.type('e')

    assert lookup.items[0].lookupString == 'File'
    assert lookup.items[1].lookupString == 'fileSize'
    assert lookup.currentItem == lookup.items[1]
  }

  void testNoGenericsWhenChoosingWithParen() { doTest('Ma(') }

  void testNoClosingWhenChoosingWithParenBeforeIdentifier() { doTest '(' }

  void testPackageInMemberType() { doTest() }

  void testConstantInAnno() { doTest() }

  void testCharsetName() {
    myFixture.addClass("package java.nio.charset; public class Charset { public static Charset forName(String s) {} }")
    configureByTestName()
    assert myFixture.lookupElementStrings.contains('UTF-8')
  }

  void testInnerClassInExtendsGenerics() {
    def text = "package bar; class Foo extends List<Inne<caret>> { public static class Inner {} }"
    myFixture.configureFromExistingVirtualFile(myFixture.addClass(text).containingFile.virtualFile)
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult(text.replace('Inne<caret>', 'Foo.Inner<caret>'))
  }

  void testClassNameDot() { doTest('.') }

  void testClassNameDotBeforeCall() {
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

  void testNoReturnAfterDot() {
    configure()
    assert !('return' in myFixture.lookupElementStrings)
  }

  void testDuplicateExpectedTypeInTypeArgumentList() {
    configure()
    def items = myFixture.lookupElements.findAll { it.lookupString == 'String' }
    assert items.size() == 1
    assert LookupElementPresentation.renderElement(items[0]).tailText == ' (java.lang)'
  }

  void testDuplicateInnerClass() {
    configure()
    def items = myFixture.lookupElements.findAll { it.lookupString == 'Inner' }
    assert items.size() == 1
  }

  void testSameSignature() {
    configure()
    myFixture.assertPreferredCompletionItems(0, 's', 's, file', 's, file, a')
    lookup.setCurrentItem(lookup.items[2])
    myFixture.type('\n')
    checkResult()
  }

  void testNoParenthesesAroundCallQualifier() { doTest() }

  void testAllAssertClassesMethods() {
    myFixture.addClass 'package foo; public class Assert { public static boolean foo() {} }'
    myFixture.addClass 'package bar; public class Assert { public static boolean bar() {} }'
    configure()
    assert myFixture.lookupElementStrings == ['Assert.bar', 'Assert.foo']
    myFixture.type '\n'
    checkResult()
  }

  void testCastVisually() {
    configure()
    def p = LookupElementPresentation.renderElement(myFixture.lookupElements[0])
    assert p.itemText == 'getValue'
    assert p.itemTextBold
    assert p.typeText == 'Foo'
  }

  void testSuggestEmptySet() {
    configure()
    assert 'emptySet' == myFixture.lookupElementStrings[0]
    type '\n'
    checkResult()
  }

  void testSuggestAllTypeArguments() {
    configure()
    assert 'String, List<String>' == lookup.items[0].lookupString
    assert 'String, List<String>' == LookupElementPresentation.renderElement(lookup.items[0]).itemText
    type '\n'
    checkResult()
  }

  void testMakeMultipleArgumentsFinalWhenInInner() {
    configure()
    def item = lookup.items.find { 'a, b' == it.lookupString }
    assert item
    lookup.currentItem = item
    type '\n'
    checkResult()
  }

  void testNoFinalInAnonymousConstructor() { doTest() }

  void testListArrayListCast() { doTest('\n') }

  void testInterfaceImplementationNoCast() { doTest() }

  void testStaticallyImportedMethodsBeforeExpression() { doTest() }

  void testInnerChainedReturnType() { doTest() }

  void testOverwriteGenericsAfterNew() { doTest('\n') }

  private CommonCodeStyleSettings getCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE)
  }

  void testCompatibleInterfacesCast() {
    configure()
    assert myFixture.lookupElementStrings.containsAll(['foo', 'bar'])
  }

  void testDontAutoInsertMiddleMatch() {
    configure()
    checkResult()
    assert lookup.items.size() == 1
  }

  void testImplementViaCompletion() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'private', 'protected', 'public', 'public void run'
    def item = lookup.items[3]

    def p = LookupElementPresentation.renderElement(item)
    assert p.itemText == 'public void run'
    assert p.tailText == '(String t, int myInt) {...}'
    assert p.typeText == 'Foo'

    lookup.currentItem = item
    myFixture.type('\n')
    checkResult()
  }

  void testImplementViaCompletionWithGenerics() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'public void methodWithGenerics', 'public void methodWithTypeParam'
    assert LookupElementPresentation.renderElement(lookup.items[0]).tailText == '(List k) {...}'
    assert LookupElementPresentation.renderElement(lookup.items[1]).tailText == '(K k) {...}'
  }

  void testImplementViaOverrideCompletion() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'Override', 'public void run'
    lookup.currentItem = lookup.items[1]
    myFixture.type('\n')
    checkResult()
  }

  void testStrikeOutDeprecatedSuperMethods() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'void foo1', 'void foo2'
    assert !LookupElementPresentation.renderElement(lookup.items[0]).strikeout
    assert LookupElementPresentation.renderElement(lookup.items[1]).strikeout
  }

  void testAccessorViaCompletion() {
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
    assert p.tailText == '(int field) {...}'
    assert !p.typeText

    lookup.currentItem = getter
    myFixture.type('\n')
    checkResult()
  }

  void testNoSetterForFinalField() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'public', 'public int getFinalField'
    assert !myFixture.lookupElements.find { it.lookupString == 'public void setFinalField' }
    assert !myFixture.lookupElements.find { it.lookupString == 'public int getCONSTANT' }
  }

  void testBraceOnNextLine() {
    codeStyleSettings.BRACE_STYLE = CommonCodeStyleSettings.NEXT_LINE
    doTest()
  }

  void testDoForceBraces() {
    codeStyleSettings.DOWHILE_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    doTest('\n')
  }

  void testMulticaretSingleItemInsertion() {
    doTest()
  }

  void testMulticaretMethodWithParen() {
    doTest()
  }

  void testMulticaretTyping() {
    configure()
    assert lookup
    type('p')
    assert lookup
    type('\n')
    checkResult()
  }

  void testMulticaretCompletionFromNonPrimaryCaret() {
    configure()
    myFixture.assertPreferredCompletionItems(0, "arraycopy")
  }

  void testMulticaretCompletionFromNonPrimaryCaretWithTab() {
    doTest '\t'
  }

  void "test complete lowercase class name"() {
    myFixture.addClass("package foo; public class myClass {}")
    myFixture.configureByText "a.java", """
class Foo extends my<caret>
"""
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.checkResult '''import foo.myClass;

class Foo extends myClass
'''
  }

  void "test don't show static inner class after instance qualifier"() {
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

  void "test show static member after instance qualifier when nothing matches"() {
    myFixture.configureByText "a.java", "class Foo{{ \"\".<caret> }}"
    myFixture.completeBasic()
    assert !('valueOf' in myFixture.lookupElementStrings)
    ((LookupImpl)myFixture.lookup).hide()
    myFixture.type 'val'
    myFixture.completeBasic()
    assert ('valueOf' in myFixture.lookupElementStrings)
  }

  void testNoMathTargetMethods() { doAntiTest() }

  void testNoLowercaseClasses() {
    myFixture.addClass("package foo; public class abcdefgXxx {}")
    doAntiTest()
    myFixture.complete(CompletionType.BASIC, 2)
    assertStringItems('abcdefgXxx')
  }

  void testProtectedFieldInAnotherPackage() {
    myFixture.addClass("package foo; public class Super { protected String myString; }")
    doTest()
  }

  void testUnimportedStaticInnerClass() {
    myFixture.addClass("package foo; public class Super { public static class Inner {} }")
    doTest()
  }

  void testNoJavaLangPackagesInImport() { doAntiTest() }

  void testNoStaticDuplicatesFromExpectedMemberFactories() {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.assertPreferredCompletionItems(0, "xcreateZoo", "xcreateElephant")
  }

  void testNoInaccessibleCompiledElements() {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    checkResultByFile(getTestName(false) + ".java")
    assertEmpty(myItems)
    assertNull(getLookup())
  }

  void "test code cleanup during completion generation"() {
    myFixture.configureByText "a.java", "class Foo {int i; ge<caret>}"
    myFixture.enableInspections(new UnqualifiedFieldAccessInspection())
    myFixture.complete(CompletionType.BASIC)
    UIUtil.dispatchAllInvocationEvents()
    myFixture.checkResult '''class Foo {int i;

    public int getI() {
        return this.i;
    }
}'''
  }

  void testIndentingForSwitchCase() { doTest() }

  void testIncrementalCopyReparse() {
    ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).disableBackgroundCommit(myFixture.testRootDisposable)
    
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

  void testShowMostSpecificOverride() {
    configure()
    assert 'B' == LookupElementPresentation.renderElement(myFixture.lookup.items[0]).typeText
  }

  void testShowMostSpecificOverrideOnlyFromClass() {
    configure()
    assert 'Door' == LookupElementPresentation.renderElement(myFixture.lookup.items[0]).typeText
  }

  void testShowVarInitializers() {
    configure()
    assert LookupElementPresentation.renderElement(myFixture.lookup.items[0]).tailText == '( "x")'
    assert LookupElementPresentation.renderElement(myFixture.lookup.items[1]).tailText == '("y") {...}'
    assert !LookupElementPresentation.renderElement(myFixture.lookup.items[2]).tailText
    assert LookupElementPresentation.renderElement(myFixture.lookup.items[3]).tailText == ' ( = 42)'
    assert LookupElementPresentation.renderElement(myFixture.lookup.items[3]).tailFragments[0].italic
  }

  void testSuggestInterfaceArrayWhenObjectIsExpected() {
    configure()
    assert LookupElementPresentation.renderElement(myFixture.lookup.items[0]).tailText.contains('{...}')
    assert LookupElementPresentation.renderElement(myFixture.lookup.items[1]).tailText.contains('[]')
  }

  void testSuggestInterfaceArrayWhenObjectArrayIsExpected() {
    configure()
    assert LookupElementPresentation.renderElement(myFixture.lookup.items[0]).tailText.contains('{...}')
    assert LookupElementPresentation.renderElement(myFixture.lookup.items[1]).tailText.contains('[]')
  }

  void testDispreferPrimitiveTypesInCallArgs() throws Throwable {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    configure()
    myFixture.assertPreferredCompletionItems 0, "dx", "doo", "Doo", "double"
  }

  void testCopyConstructor() { doTest('\n') }

  void testGetClassType() {
    configure()
    assert 'Class<? extends Number>' == LookupElementPresentation.renderElement(myFixture.lookupElements[0]).typeText
  }

  void testNonImportedClassAfterNew() {
    def uClass = myFixture.addClass('package foo; public class U {}')
    myFixture.configureByText('a.java', 'class X {{ new U<caret>x }}')
    myFixture.completeBasic()
    assert myFixture.lookupElements[0].object == uClass
  }

  void testSuggestClassNamesForLambdaParameterTypes() { doTest('\n') }

  void testOnlyExtendsSuperInWildcard() {
    CodeInsightSettings.instance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE

    configure()
    assert myFixture.lookupElementStrings == ['extends', 'super']
    LookupManager.getInstance(project).hideActiveLookup()

    myFixture.type('n')
    assert !myFixture.completeBasic()
    myFixture.type('\b')
    checkResultByFile(getTestName(false) + ".java")
  }

  void testChainInLambdaBinary() {
    codeStyleSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    myFixture.addClass("package pkg; public class PathUtil { public static String toSystemDependentName() {} }")
    doTest('\n')
  }
  
  void testPairAngleBracketDisabled() {
    CodeInsightSettings.instance.AUTOINSERT_PAIR_BRACKET = false
    doTest('<')
  }

  void testDuplicateGenericMethodSuggestionWhenInheritingFromRawType() {
    configure()
    assert myFixture.lookupElementStrings == ['indexOf']
  }

  void testDuplicateEnumValueOf() {
    configure()
    assert myFixture.lookupElements.collect { LookupElementPresentation.renderElement(it).itemText } == ['Bar.valueOf', 'Foo.valueOf', 'Enum.valueOf']
  }
  
  void testTypeArgumentInCast() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'String'
  }

}
