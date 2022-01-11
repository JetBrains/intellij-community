// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.*
import com.intellij.psi.augment.PsiAugmentProvider
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.impl.source.PsiExtensibleClass
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ServiceContainerUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import com.siyeh.ig.style.UnqualifiedFieldAccessInspection
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import java.util.stream.Collectors

@CompileStatic
class NormalCompletionTest extends NormalCompletionTestCase {

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_9
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

  @NeedsIndex.ForStandardLibrary
  void testSimpleVariable() throws Exception { doTest('\n') }

  void testTypeParameterItemPresentation() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "Param" == presentation.itemText
    assert presentation.tailText == " type parameter of Foo"
    assert !presentation.typeText
    assert !presentation.icon
    assert !presentation.itemTextBold

    presentation = renderElement(myItems[1])
    assert "Param2" == presentation.itemText
    assert presentation.tailText == " type parameter of goo"
  }

  void testDisplayDefaultValueInAnnotationMethods() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "myInt" == presentation.itemText
    assert presentation.tailText == " default 42"
    assert presentation.tailFragments[0].grayed
    assert !presentation.typeText
    assert !presentation.itemTextBold

    presentation = renderElement(myItems[1])
    assert "myString" == presentation.itemText
    assert presentation.tailText == ' default "unknown"'
  }

  @NeedsIndex.ForStandardLibrary
  void testMethodItemPresentation() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "equals" == presentation.itemText
    assert "(Object anObject)" == presentation.tailText
    assert "boolean" == presentation.typeText

    assert !presentation.tailFragments.any { it.grayed }
    assert presentation.itemTextBold
  }

  void testFieldItemPresentationGenerics() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[0])
    assert "target" == presentation.itemText
    assert !presentation.tailText
    assert "String" == presentation.typeText
  }

  @NeedsIndex.ForStandardLibrary
  void testMethodItemPresentationGenerics() {
    configure()
    LookupElementPresentation presentation = renderElement(myItems[1])
    assert "add" == presentation.itemText
    assert "(int index, String element)" == presentation.tailText
    assert "void" == presentation.typeText

    presentation = renderElement(myItems[0])
    assert "(String e)" == presentation.tailText
    assert "boolean" == presentation.typeText

    assert !presentation.tailFragments.any { it.grayed }
    assert presentation.itemTextBold
  }

  void testPreferLongerNamesOption() throws Exception {
    configureByFile("PreferLongerNamesOption.java")

    assertEquals(3, myItems.length)
    assertEquals("abcdEfghIjk", myItems[0].getLookupString())
    assertEquals("efghIjk", myItems[1].getLookupString())
    assertEquals("ijk", myItems[2].getLookupString())

    LookupManager.getInstance(getProject()).hideActiveLookup()

    JavaCodeStyleSettings.getInstance(getProject()).PREFER_LONGER_NAMES = false
      configureByFile("PreferLongerNamesOption.java")

      assertEquals(3, myItems.length)
      assertEquals("ijk", myItems[0].getLookupString())
      assertEquals("efghIjk", myItems[1].getLookupString())
      assertEquals("abcdEfghIjk", myItems[2].getLookupString())
  }

  void testSCR7208() throws Exception {
    configureByFile("SCR7208.java")
  }

  @NeedsIndex.ForStandardLibrary
  void testProtectedFromSuper() throws Exception {
    configureByFile("ProtectedFromSuper.java")
    Arrays.sort(myItems)
    assertTrue("Exception not found", Arrays.binarySearch(myItems, "xxx") > 0)
  }

  @NeedsIndex.ForStandardLibrary
  void testBeforeInitialization() throws Exception {
    configureByFile("BeforeInitialization.java")
    assertNotNull(myItems)
    assertTrue(myItems.length > 0)
  }

  @NeedsIndex.ForStandardLibrary
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
    CodeInsightSettings.instance.setCompletionCaseSensitive(CodeInsightSettings.FIRST_LETTER)
    CodeInsightSettings.instance.setSelectAutopopupSuggestionsByChars(false)
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

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
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

  void testEnumInTypeAnnotation() { doTest() }

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

  void testFieldOfLocalClass() {
    configure()
    assert renderElement(myItems[0]).itemText == 'field'
    type('\t')
    checkResult()
  }

  void testPackageInAnnoParam() throws Throwable {
    doTest()
  }

  void testAnonymousTypeParameter() throws Throwable { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testClassLiteralInAnnoParam() throws Throwable {
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
  void testNoForceBraces() {
    codeStyleSettings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS
    doTest('\n')
  }

  void testExcludeStringBuffer() throws Throwable {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, StringBuffer.name)
    configure()
    assert !('StringBuffer' in myFixture.lookupElementStrings)
  }

  @NeedsIndex.Full
  void testExcludeInstanceInnerClasses() throws Throwable {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "foo")
    myFixture.addClass 'package foo; public class Outer { public class Inner {} }'
    myFixture.addClass 'package bar; public class Inner {}'
    configure()
    assert 'bar.Inner' == ((JavaPsiClassReferenceElement)myFixture.lookupElements[0]).qualifiedName
    assert myFixture.lookupElementStrings == ['Inner']
  }

  @NeedsIndex.Full
  void testExcludedInstanceInnerClassCreation() throws Throwable {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "foo")
    myFixture.addClass 'package foo; public class Outer { public class Inner {} }'
    myFixture.addClass 'package bar; public class Inner {}'
    configure()
    assert 'foo.Outer.Inner' == ((JavaPsiClassReferenceElement)myFixture.lookupElements[0]).qualifiedName
    assert myFixture.lookupElementStrings == ['Inner']
  }

  @NeedsIndex.Full
  void testExcludedInstanceInnerClassQualifiedReference() throws Throwable {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "foo")
    myFixture.addClass 'package foo; public class Outer { public class Inner {} }'
    myFixture.addClass 'package bar; public class Inner {}'
    configure()
    assert 'foo.Outer.Inner' == ((JavaPsiClassReferenceElement)myFixture.lookupElements[0]).qualifiedName
    assert myFixture.lookupElementStrings == ['Inner']
  }

  @NeedsIndex.Full
  void testStaticMethodOfExcludedClass() {
    JavaProjectCodeInsightSettings.setExcludedNames(project, myFixture.testRootDisposable, "foo")
    myFixture.addClass 'package foo; public class Outer { public static void method() {} }'
    configure()
    assert myFixture.lookupElementStrings == ['method']
  }

  @NeedsIndex.Full
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

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for int hashCode lookup preventing autocompletion and additional \n)")
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

  @NeedsIndex.ForStandardLibrary
  void testLocalClassTwice() throws Throwable {
    configure()
    assertOrderedEquals myFixture.lookupElementStrings, 'Zoooz', 'Zooooo', 'ZipOutputStream'
  }

  @NeedsIndex.ForStandardLibrary
  void testLocalTopLevelConflict() throws Throwable {
    configure()
    assertOrderedEquals myFixture.lookupElementStrings, 'Zoooz', 'Zooooo', 'ZipOutputStream'
  }

  @NeedsIndex.ForStandardLibrary
  void testFinalBeforeMethodCall() throws Throwable {
    configure()
    assertStringItems 'final', 'finalize'
  }

  void testMethodCallAfterFinally() { doTest() }

  void testPrivateInAnonymous() throws Throwable { doTest() }

  void testStaticMethodFromOuterClass() {
    configure()
    assertStringItems 'foo', 'A.foo', 'for'
    assert renderElement(myItems[1]).itemText == 'A.foo'
    selectItem(myItems[1])
    checkResult()
  }

  void testInstanceMethodFromOuterClass() {
    configure()
    assertStringItems 'foo', 'A.this.foo', 'for'
    assert renderElement(myItems[1]).itemText == 'A.this.foo'
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

  @NeedsIndex.ForStandardLibrary(reason = "On emptly indices 'foo' is the only item, so is not filtered out in  JavaCompletionProcessor.dispreferStaticAfterInstance")
  void testAccessStaticViaInstanceSecond() throws Throwable {
    configure()
    assert !('foo' in myFixture.lookupElementStrings)
    myFixture.complete(CompletionType.BASIC, 2)
    myFixture.assertPreferredCompletionItems 0, 'foo'
    myFixture.type('\n')
    checkResult()
  }

  void testAccessInstanceFromStaticSecond() throws Throwable {
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    checkResult()
  }

  void testBreakLabel() {
    myFixture.configureByText("a.java", """
      class a {{
        foo: while (true) break <caret>
      }}""".stripIndent())
    complete()
    assert myFixture.lookupElementStrings == ['foo']
  }

  void testContinueLabel() {
    myFixture.configureByText("a.java", """
      class a {{
        foo: while (true) continue <caret>
      }}""".stripIndent())
    complete()
    assert myFixture.lookupElementStrings == ['foo']
  }

  void testContinueLabelTail() {
    myFixture.configureByText("a.java", """
      class a {{
        foo: while (true) con<caret>
      }}""".stripIndent())
    complete()
    myFixture.checkResult("""
      class a {{
        foo: while (true) continue <caret>
      }}""".stripIndent())
  }

  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
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

  @NeedsIndex.ForStandardLibrary
  void testBooleanLiterals() throws Throwable {
    doTest('\n')
  }

  void testDoubleBooleanInParameter() throws Throwable {
    configure()
    assertFirstStringItems("boolean", "byte")
  }

  void testDoubleConstant() throws Throwable {
    configure()
    assertStringItems("Intf.XFOO", "XFOO")
  }

  void testNotOnlyKeywordsInsideSwitch() throws Throwable {
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.Full
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

  @NeedsIndex.ForStandardLibrary
  void testNoSpaceInParensWithoutParams() throws Throwable {
    codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    try {
      doTest()
    }
    finally {
      codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = false
    }
  }

  @NeedsIndex.ForStandardLibrary
  void testTwoSpacesInParensWithParams() throws Throwable {
    codeStyleSettings.SPACE_WITHIN_METHOD_CALL_PARENTHESES = true
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
  void testQualifierAsPackage() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    selectItem(myItems[0])
    checkResult()
  }

  @NeedsIndex.ForStandardLibrary
  void testQualifierAsPackage2() throws Throwable {
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
  void testQualifierAsPackage3() throws Throwable {
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
  void testPreselectEditorSelection() {
    configure()
    assert lookup.currentItem != myFixture.lookupElements[0]
    assert 'finalize' == lookup.currentItem.lookupString
  }

  void testNoMethodsInNonStaticImports() {
    configure()
    assertStringItems("*")
  }

  @NeedsIndex.ForStandardLibrary
  void testMembersInStaticImports() { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
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

  void testShadowedTypeParameter() {
    configure()
    assert myFixture.lookupElementStrings == ['MyParam']
  }

  @NeedsIndex.ForStandardLibrary
  void testMethodReturnType() { doTest('\n') }

  void testMethodReturnTypeNoSpace() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    selectItem(myItems[0])
    checkResult()
  }

  void testEnumWithoutConstants() throws Throwable {
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
  void testDoWhileMethodCall() throws Throwable {
    doTest()
  }

  void testSecondTypeParameterExtends() throws Throwable {
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
  void testMethodMergingMinimalTail() { doTest() }

  void testAnnotationQualifiedName() throws Throwable {
    doTest()
  }

  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  void testClassNameAnonymous() throws Throwable {
    doTest('\n')
  }

  @NeedsIndex.SmartMode(reason = "For now ConstructorInsertHandler.createOverrideRunnable doesn't work in dumb mode")
  void testClassNameWithInner() throws Throwable {
    configure()
    assertStringItems 'Zzoo', 'Zzoo.Impl'
    type '\n'
    checkResult()
  }

  void testClassNameWithInner2() throws Throwable { doTest('\n') }

  void testClassNameWithInstanceInner() throws Throwable { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testDoubleFalse() throws Throwable {
    configureByFile(getTestName(false) + ".java")
    assertFirstStringItems("false", "fefefef", "float", "finalize")
  }

  void testSameNamedVariableInNestedClasses() throws Throwable {
    configure()
    myFixture.assertPreferredCompletionItems 0, "ffid", "Beda.this.ffid"
    selectItem(myItems[1])
    checkResult()
  }

  void testHonorUnderscoreInPrefix() throws Throwable {
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
  void testNoSemicolonAfterExistingParenthesesEspeciallyIfItsACast() throws Throwable { doTest() }

  void testReturningTypeVariable() throws Throwable { doTest() }

  void testReturningTypeVariable2() throws Throwable { doTest() }

  void testReturningTypeVariable3() throws Throwable { doTest() }

  @NeedsIndex.ForStandardLibrary
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

  void testFinalInForLoop() throws Throwable {
    configure()
    assertStringItems 'final'
  }

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.Full
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

  @NeedsIndex.ForStandardLibrary
  void testSecondAnonymousClassParameter() { doTest() }

  void testSpaceAfterReturn() throws Throwable {
    configure()
    type '\n'
    checkResult()
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

  @NeedsIndex.SmartMode(reason = "MethodCallFixer.apply needs smart mode to count number of parameters")
  void testSmartEnterGuessArgumentCount() throws Throwable { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }

  void testSmartEnterInsideArrayBrackets() { doTest(Lookup.COMPLETE_STATEMENT_SELECT_CHAR as String) }

  void testTabReplacesMethodNameWithLocalVariableName() throws Throwable { doTest('\t') }

  void testMethodParameterAnnotationClass() throws Throwable { doTest() }

  @NeedsIndex.Full
  void testInnerAnnotation() {
    configure()
    assert myFixture.lookupElementStrings == ['Dependency']
    type '\t'
    checkResult()
  }

  void testPrimitiveCastOverwrite() throws Throwable { doTest() }

  void testClassReferenceInFor() throws Throwable { doTest ' ' }

  void testClassReferenceInFor2() throws Throwable { doTest ' ' }

  void testClassReferenceInFor3() throws Throwable {
    CodeInsightSettings.instance.setCompletionCaseSensitive(CodeInsightSettings.NONE)
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

  @NeedsIndex.Full
  void testExpectedTypeMembersVersusStaticImports() throws Throwable {
    configure()
    assertStringItems('XFOO', 'XFOX')
  }

  @NeedsIndex.Full
  void testSuggestExpectedTypeMembersNonImported() throws Throwable {
    myFixture.addClass("package foo; public class Super { public static final Super FOO = null; }")
    myFixture.addClass("package foo; public class Usage { public static void foo(Super s) {} }")
    doTest('\n')
  }

  @NeedsIndex.Full
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

  void testLiveTemplatePrefixTab() throws Throwable { doTest('\t') }

  void testOnlyAnnotationsAfterAt() throws Throwable { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testOnlyAnnotationsAfterAt2() throws Throwable { doTest('\n') }

  void testAnnotationBeforeIdentifier() { doTest('\n') }

  void testAnnotationBeforeQualifiedReference() { doTest('\n') }

  void testAnnotationBeforeIdentifierFinishWithSpace() { doTest(' ') }

  @NeedsIndex.ForStandardLibrary
  void testOnlyExceptionsInCatch1() throws Exception { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testOnlyExceptionsInCatch2() throws Exception { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testOnlyExceptionsInCatch3() throws Exception { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testOnlyExceptionsInCatch4() throws Exception { doTest('\n') }

  void testCommaAfterVariable() throws Throwable { doTest(',') }

  void testClassAngleBracket() throws Throwable { doTest('<') }

  void testNoArgsMethodSpace() throws Throwable { doTest(' ') }

  void testClassSquareBracket() throws Throwable { doTest('[') }

  void testPrimitiveSquareBracket() throws Throwable { doTest('[') }

  void testVariableSquareBracket() throws Throwable { doTest('[') }

  void testMethodSquareBracket() throws Throwable { doTest('[') }

  void testMethodParameterTypeDot() throws Throwable { doAntiTest() }

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.Full
  void testSuperProtectedMethod() throws Throwable {
    myFixture.addClass """package foo;
      public class Bar {
          protected void foo() { }
      }"""
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
  void testOuterSuperMethodCall() {
    configure()
    assert 'Class2.super.put' == renderElement(myItems[0]).itemText
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

  @NeedsIndex.ForStandardLibrary
  void testAfterCommonPrefix() throws Throwable {
    configure()
    type 'eq'
    assertFirstStringItems("equals", "equalsIgnoreCase")
    complete()
    assertFirstStringItems("equals", "equalsIgnoreCase")
    type '('
    checkResult()
  }

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.Full
  void testImportStringValue() throws Throwable {
    myFixture.addClass("package foo; public class StringValue {}")
    myFixture.addClass("package java.lang; class StringValue {}")
    configure()
    myFixture.complete(CompletionType.BASIC, 2)
    type ' '
    checkResult()
  }

  void testPrimitiveArrayWithRBrace() throws Throwable { doTest '[' }

  @NeedsIndex.Full
  void testSuggestMembersOfStaticallyImportedClasses() throws Exception {
    myFixture.addClass("""package foo;
    public class Foo {
      public static void foo() {}
      public static void bar() {}
    }
    """)
    doTest('\n')
  }

  @NeedsIndex.Full
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

  @NeedsIndex.Full
  void testSuggestMembersOfStaticallyImportedClassesConflictWithLocalMethod() throws Exception {
    myFixture.addClass("""package foo;
    public class Foo {
      public static void foo() {}
      public static void bar() {}
    }
    """)
    configure()
    myFixture.assertPreferredCompletionItems 0, 'bar', 'bar'
    assert renderElement(myFixture.lookupElements[1]).itemText == 'Foo.bar'
    myFixture.lookup.currentItem = myFixture.lookupElements[1]
    myFixture.type '\t'
    checkResult()
  }

  @NeedsIndex.Full
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

  void testNoModifierListOverwrite() { doTest('\t') }

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

  @NeedsIndex.Full
  void testNonImportedClassInAnnotation() {
    myFixture.addClass("package foo; public class XInternalTimerServiceController {}")
    myFixture.configureByText "a.java", """
class XInternalError {}

@interface Anno { Class value(); }

@Anno(XInternal<caret>)
"""
    myFixture.complete(CompletionType.BASIC, 2)
    assertFirstStringItems "XInternalError", "XInternalTimerServiceController"
  }

  @NeedsIndex.Full
  void testNonImportedAnnotationClass() {
    myFixture.addClass("package foo; public @interface XAnotherAnno {}")
    configure()
    type('X')
    assertFirstStringItems "XAnno", "XAnotherAnno"
  }

  @NeedsIndex.ForStandardLibrary
  void testMetaAnnotation() {
    myFixture.configureByText "a.java", "@<caret> @interface Anno {}"
    myFixture.complete(CompletionType.BASIC)
    assert myFixture.lookup.items.find { it.lookupString == 'Retention' }
  }

  void testAnnotationClassFromWithinAnnotation() { doTest() }

  @NeedsIndex.Full
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
    CodeInsightSettings.instance.setCompletionCaseSensitive(CodeInsightSettings.NONE)
    myFixture.configureByText "a.java", "import java.io.*; class Foo {{ int fileSize; fil<caret>x }}"
    myFixture.completeBasic()
    assert lookup.currentItem.lookupString == 'fileSize'
    myFixture.type('e')

    assert lookup.items[0].lookupString == 'File'
    assert lookup.items[1].lookupString == 'fileSize'
    assert lookup.currentItem == lookup.items[1]
  }

  @NeedsIndex.ForStandardLibrary
  void testNoGenericsWhenChoosingWithParen() { doTest('Ma(') }

  @NeedsIndex.ForStandardLibrary
  void testNoClosingWhenChoosingWithParenBeforeIdentifier() { doTest '(' }

  void testPackageInMemberType() { doTest() }
  void testPackageInMemberTypeGeneric() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testConstantInAnno() { doTest('\n') }

  @NeedsIndex.SmartMode(reason = "Smart mode needed for EncodingReferenceInjector to provide EncodingReference with variants")
  void testCharsetName() {
    myFixture.addClass("package java.nio.charset; public class Charset { public static Charset forName(String s) {} }")
    configureByTestName()
    assert myFixture.lookupElementStrings.contains('UTF-8')
  }

  @NeedsIndex.Full
  void testInnerClassInExtendsGenerics() {
    def text = "package bar; class Foo extends List<Inne<caret>> { public static class Inner {} }"
    myFixture.configureFromExistingVirtualFile(myFixture.addClass(text).containingFile.virtualFile)
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult(text.replace('Inne<caret>', 'Foo.Inner<caret>'))
  }

  @NeedsIndex.ForStandardLibrary
  void testClassNameDot() { doTest('.') }

  @NeedsIndex.Full
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

  @NeedsIndex.ForStandardLibrary
  void testDuplicateExpectedTypeInTypeArgumentList() {
    configure()
    def items = myFixture.lookupElements.findAll { it.lookupString == 'String' }
    assert items.size() == 1
    assert renderElement(items[0]).tailText == ' (java.lang)'
  }

  @NeedsIndex.Full
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

  @NeedsIndex.ForStandardLibrary
  void testNoParenthesesAroundCallQualifier() { doTest() }

  @NeedsIndex.Full
  void testAllAssertClassesMethods() {
    myFixture.addClass 'package foo; public class Assert { public static boolean foo() {} }'
    myFixture.addClass 'package bar; public class Assert { public static boolean bar() {} }'
    configure()
    assert myFixture.lookupElementStrings == ['Assert.bar', 'Assert.foo']
    myFixture.type '\n'
    checkResult()
  }

  @NeedsIndex.Full
  void testCastVisually() {
    configure()
    def p = renderElement(myFixture.lookupElements[0])
    assert p.itemText == 'getValue'
    assert p.itemTextBold
    assert p.typeText == 'Foo'
  }

  @NeedsIndex.ForStandardLibrary
  void testSuggestEmptySet() {
    configure()
    assert 'emptySet' == myFixture.lookupElementStrings[0]
    type '\n'
    checkResult()
  }

  @NeedsIndex.ForStandardLibrary
  void testSuggestAllTypeArguments() {
    configure()
    assert 'String, List<String>' == lookup.items[0].lookupString
    assert 'String, List<String>' == renderElement(lookup.items[0]).itemText
    type '\n'
    checkResult()
  }

  void testNoFinalInAnonymousConstructor() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testListArrayListCast() { doTest('\n') }

  void testInterfaceImplementationNoCast() { doTest() }

  @NeedsIndex.Full
  void testStaticallyImportedMethodsBeforeExpression() { doTest() }

  @NeedsIndex.Full
  void testInnerChainedReturnType() { doTest() }

  private CommonCodeStyleSettings getCodeStyleSettings() {
    return CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE)
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

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for method implementations)")
  void testImplementViaCompletion() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'private', 'protected', 'public', 'public void run'
    def item = lookup.items[3]

    def p = renderElement(item)
    assert p.itemText == 'public void run'
    assert p.tailText == '(String t, int myInt) {...}'
    assert p.typeText == 'Foo'

    lookup.currentItem = item
    myFixture.type('\n')
    checkResult()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for implementing methods)")
  void testImplementViaCompletionWithGenerics() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'public void methodWithGenerics', 'public void methodWithTypeParam'
    assert renderElement(lookup.items[0]).tailText == '(List k) {...}'
    assert renderElement(lookup.items[1]).tailText == '(K k) {...}'
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  void testImplementViaOverrideCompletion() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'Override', 'Override/Implement methods...', 'public void run'
    lookup.currentItem = lookup.items[2]
    myFixture.type('\n')
    checkResult()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  void testSuggestToOverrideMethodsWhenTypingOverrideAnnotation() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'Override', 'Override/Implement methods...'
    lookup.currentItem = lookup.items[1]
    myFixture.type('\n')
    checkResult()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  void testSuggestToOverrideMethodsWhenTypingOverrideAnnotationBeforeMethod() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'Override', 'Override/Implement methods...'
    lookup.currentItem = lookup.items[1]
    myFixture.type('\n')
    checkResult()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants provides dialog option in smart mode only")
  void testSuggestToOverrideMethodsInMulticaretMode() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'Override', 'Override/Implement methods...'
    lookup.currentItem = lookup.items[1]
    myFixture.type('\n')
    checkResult()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for implementing methods)")
  void testStrikeOutDeprecatedSuperMethods() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'void foo1', 'void foo2'
    assert !renderElement(lookup.items[0]).strikeout
    assert renderElement(lookup.items[1]).strikeout
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for equals() and hashCode())")
  void testInvokeGenerateEqualsHashCodeOnOverrideCompletion() {
    configure()
    assert myFixture.lookupElementStrings.size() == 2
    lookup.setSelectedIndex(1)
    type('\n')
    checkResult()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for 'toString()')")
  void testInvokeGenerateToStringOnOverrideCompletion() {
    configure()
    assert myFixture.lookupElementStrings.size() == 2
    lookup.setSelectedIndex(1)
    type('\n')
    checkResult()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for equals() and hashCode())")
  void testDontGenerateEqualsHashCodeOnOverrideCompletion() {
    configure()
    type('\n')
    checkResult()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for 'toString()')")
  void testDontGenerateToStringOnOverrideCompletion() {
    configure()
    type('\n')
    checkResult()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters and setters)")
  void testAccessorViaCompletion() {
    configure()

    def getter = myFixture.lookupElements.find { it.lookupString == 'public int getField' }
    def setter = myFixture.lookupElements.find { it.lookupString == 'public void setField' }
    assert getter : myFixture.lookupElementStrings
    assert setter : myFixture.lookupElementStrings

    def p = renderElement(getter)
    assert p.itemText == getter.lookupString
    assert p.tailText == '() {...}'
    assert !p.typeText

    p = renderElement(setter)
    assert p.itemText == setter.lookupString
    assert p.tailText == '(int field) {...}'
    assert !p.typeText

    lookup.currentItem = getter
    myFixture.type('\n')
    checkResult()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters and setters)")
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

  @NeedsIndex.ForStandardLibrary
  void testMulticaretMethodWithParen() {
    doTest()
  }

  @NeedsIndex.ForStandardLibrary
  void testMulticaretTyping() {
    configure()
    assert lookup
    type('p')
    assert lookup
    type('\n')
    checkResult()
  }

  @NeedsIndex.ForStandardLibrary
  void testMulticaretCompletionFromNonPrimaryCaret() {
    configure()
    myFixture.assertPreferredCompletionItems(0, "arraycopy")
  }

  void testMulticaretCompletionFromNonPrimaryCaretWithTab() {
    doTest '\t'
  }

  @NeedsIndex.Full
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

  void testNoClassesWithDollar() {
    myFixture.addClass('package some; public class $WithDollarNonImported {}')
    myFixture.addClass('package imported; public class $WithDollarImported {}')
    doAntiTest()
  }

  void testClassesWithDollarInTheMiddle() {
    myFixture.addClass('package imported; public class Foo$WithDollarImported {}')
    configureByTestName()
    myFixture.completeBasic()
    assert 'Foo$WithDollarImported' in myFixture.lookupElementStrings
  }

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.ForStandardLibrary
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

  @NeedsIndex.Full
  void testNoLowercaseClasses() {
    myFixture.addClass("package foo; public class abcdefgXxx {}")
    doAntiTest()
    myFixture.complete(CompletionType.BASIC, 2)
    assertStringItems('abcdefgXxx')
  }

  @NeedsIndex.Full
  void testProtectedFieldInAnotherPackage() {
    myFixture.addClass("package foo; public class Super { protected String myString; }")
    doTest()
  }

  @NeedsIndex.Full
  void testUnimportedStaticInnerClass() {
    myFixture.addClass("package foo; public class Super { public static class Inner {} }")
    doTest()
  }

  void testNoJavaLangPackagesInImport() { doAntiTest() }

  @NeedsIndex.Full
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

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters)")
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

  void testShowMostSpecificOverride() {
    configure()
    assert 'B' == renderElement(myFixture.lookup.items[0]).typeText
  }

  @NeedsIndex.ForStandardLibrary
  void testShowMostSpecificOverrideOnlyFromClass() {
    configure()
    assert 'Door' == renderElement(myFixture.lookup.items[0]).typeText
  }

  void testNoOverrideWithMiddleMatchedName() {
    configure()
    assert !('public void removeTemporaryEditorNode' in myFixture.lookupElementStrings)
  }

  void testShowVarInitializers() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'FIELD1', 'FIELD2', 'FIELD3', 'FIELD4'
    def items = myFixture.lookup.items
    assert items.collect { renderElement(it).tailText } == ['( "x")', '("y") {...}', null, ' ( = 42)']
    assert renderElement(items[3]).tailFragments[0].italic
  }

  @NeedsIndex.Full
  void testShowNonImportedVarInitializers() {
    configure()
    myFixture.assertPreferredCompletionItems 1, 'Field', 'FIELD1', 'FIELD2', 'FIELD3', 'FIELD4'
    def fieldItems = myFixture.lookup.items[1..4]
    assert fieldItems.collect { renderElement(it).tailText } == ['( "x") in E', '("y") {...} in E', null, ' ( = 42) in E']
    assert renderElement(fieldItems[3]).tailFragments[0].italic
    assert !renderElement(fieldItems[3]).tailFragments[1].italic
  }

  @NeedsIndex.ForStandardLibrary
  void testSuggestInterfaceArrayWhenObjectIsExpected() {
    configure()
    assert renderElement(myFixture.lookup.items[0]).tailText.contains('{...}')
    assert renderElement(myFixture.lookup.items[1]).tailText.contains('[]')
  }

  @NeedsIndex.ForStandardLibrary
  void testSuggestInterfaceArrayWhenObjectArrayIsExpected() {
    configure()
    assert renderElement(myFixture.lookup.items[0]).tailText.contains('{...}')
    assert renderElement(myFixture.lookup.items[1]).tailText.contains('[]')
  }

  void testDispreferPrimitiveTypesInCallArgs() throws Throwable {
    CodeInsightSettings.instance.setCompletionCaseSensitive(CodeInsightSettings.NONE)
    configure()
    myFixture.assertPreferredCompletionItems 0, "dx", "doo", "Doo", "double"
  }

  @NeedsIndex.ForStandardLibrary
  void testCopyConstructor() { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testGetClassType() {
    configure()
    assert 'Class<? extends Number>' == renderElement(myFixture.lookupElements[0]).typeText
  }

  @NeedsIndex.Full
  void testNonImportedClassAfterNew() {
    def uClass = myFixture.addClass('package foo; public class U {}')
    myFixture.configureByText('a.java', 'class X {{ new U<caret>x }}')
    myFixture.completeBasic()
    assert myFixture.lookupElements[0].object == uClass
  }

  void testSuggestClassNamesForLambdaParameterTypes() { doTest('\n') }

  void testOnlyExtendsSuperInWildcard() {
    CodeInsightSettings.instance.setCompletionCaseSensitive(CodeInsightSettings.NONE)

    configure()
    assert myFixture.lookupElementStrings == ['extends', 'super']
    LookupManager.getInstance(project).hideActiveLookup()

    myFixture.type('n')
    assert !myFixture.completeBasic()
    myFixture.type('\b')
    checkResultByFile(getTestName(false) + ".java")
  }

  @NeedsIndex.Full
  void testChainInLambdaBinary() {
    codeStyleSettings.ALIGN_MULTILINE_BINARY_OPERATION = true
    myFixture.addClass("package pkg; public class PathUtil { public static String toSystemDependentName() {} }")
    doTest('\n')
  }

  @NeedsIndex.ForStandardLibrary
  void testPairAngleBracketDisabled() {
    CodeInsightSettings.instance.AUTOINSERT_PAIR_BRACKET = false
    doTest('<')
  }

  void testDuplicateGenericMethodSuggestionWhenInheritingFromRawType() {
    configure()
    assert myFixture.lookupElementStrings == ['indexOf']
  }

  @NeedsIndex.ForStandardLibrary
  void testDuplicateEnumValueOf() {
    configure()
    assert myFixture.lookupElements.collect { renderElement((LookupElement)it).itemText } == ['Bar.valueOf', 'Foo.valueOf', 'Enum.valueOf']
  }

  void testTypeArgumentInCast() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'String'
  }

  void testNoCallsInPackageStatement() { doAntiTest() }

  @NeedsIndex.Full
  void testTypeParameterShadowingClass() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'Tttt', 'Tttt'
    assert myFixture.lookupElements[0].object instanceof PsiTypeParameter
    assert !(myFixture.lookupElements[1].object instanceof PsiTypeParameter)
    selectItem(myFixture.lookupElements[1])
    checkResult()
  }

  void testLowercaseDoesNotMatchUnderscore() {
    configure()
    assert myFixture.lookupElementStrings == ['web']
  }

  void testLocalClassPresentation() {
    def cls = myFixture.addFileToProject('foo/Bar.java', """package foo; 
class Bar {{
    class Local {}
    Lo<caret>x
}}""")
    myFixture.configureFromExistingVirtualFile(cls.containingFile.virtualFile)
    def item = myFixture.completeBasic()[0]
    assert renderElement(item).tailText.contains('local class')
  }

  void testNoDuplicateInCast() {
    configure()
    assert myFixture.lookupElementStrings == null
  }

  void testNoNonAnnotationMethods() { doAntiTest() }

  @NeedsIndex.ForStandardLibrary
  void testPreferBigDecimalToJavaUtilInner() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'BigDecimal', 'BigDecimalLayoutForm'
  }

  @NeedsIndex.ForStandardLibrary
  void testOnlyExceptionsInMultiCatch1() { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testOnlyExceptionsInMultiCatch2() { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testOnlyResourcesInResourceList1() { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testOnlyResourcesInResourceList2() { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testOnlyResourcesInResourceList3() { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testOnlyResourcesInResourceList4() { doTest('\n') }

  void testOnlyResourcesInResourceList5() { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testMethodReferenceNoStatic() { doTest('\n') }

  void testMethodReferenceCallContext() { doTest('\n') }

  @NeedsIndex.Full
  void testDestroyingCompletedClassDeclaration() { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void testResourceParentInResourceList() {
    configureByTestName()
    assert 'MyOuterResource' == myFixture.lookupElementStrings[0]
    assert 'MyClass' in myFixture.lookupElementStrings
    myFixture.type('C\n')
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void testAfterTryWithResources() {
    configureByTestName()
    def strings = myFixture.lookupElementStrings
    assert strings.containsAll(['final', 'finally', 'int', 'Util'])
  }

  void testNewObjectHashMapWithSmartEnter() {
    configureByTestName()
    myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT)
    checkResultByFile(getTestName(false) + "_after.java")
  }

  @NeedsIndex.SmartMode(reason = "MethodCallFixer and MissingCommaFixer need resolve in smart mode")
  void testCompletionShouldNotAddExtraComma() {
    configureByTestName()
    myFixture.performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT)
    checkResultByFile(getTestName(false) + "_after.java")
  }

  @NeedsIndex.Full
  void testCompletingClassWithSameNameAsPackage() {
    myFixture.addClass("package Apple; public class Apple {}")
    doTest('\n')
  }

  void testSuggestGetInstanceMethodName() { doTest() }

  @NeedsIndex.Full(reason = "AllClassesSearchExecutor.processClassNames from JavaNoVariantsDelegator.suggestNonImportedClasses uses stub indices to provide completion, so matching Scratch class is ignored, ant so is its inner class")
  void testTabOnNewInnerClass() {
    configureByTestName()
    lookup.currentItem = myFixture.lookupElements.find { it.lookupString.contains('Inner') }
    myFixture.type('\t')
    checkResult()
  }

  @NeedsIndex.Full
  void testRemoveUnusedImportOfSameName() {
    myFixture.addClass("package foo; public class List {}")
    configureByTestName()
    lookup.currentItem = myFixture.lookupElements.find { it.object instanceof PsiClass && ((PsiClass)it.object).qualifiedName == 'java.util.List' }
    myFixture.type('\n')
    checkResult()
  }

  void "test no duplication after new with expected type parameter"() {
    myFixture.configureByText 'a.java', 'class Foo<T> { T t = new <caret> }'
    complete()
    assert myFixture.lookupElements.findAll { it.allLookupStrings.contains('T') }.size() < 2
  }

  void "test no duplication for inner class on second invocation"() {
    myFixture.configureByText 'a.java', '''
class Abc {
    class FooBar {}
    void foo() {
        FooBar<caret>x
    }
}'''
    myFixture.complete(CompletionType.BASIC, 2)
    assert myFixture.lookupElements.size() == 1
  }

  void "test smart enter wraps type arguments"() {
    myFixture.configureByText 'a.java', 'class Foo<T> { F<caret>List<String> }'
    myFixture.completeBasic()
    myFixture.type(Lookup.COMPLETE_STATEMENT_SELECT_CHAR)
    myFixture.checkResult 'class Foo<T> { Foo<List<String>><caret> }'
  }

  void testNoSuggestionsAfterEnumConstant() { doAntiTest() }

  void testPutCaretInsideParensInFixedPlusVarargOverloads() { doTest('\n') }

  void testSuggestCurrentClassInSecondSuperGenericParameter() { doTest('\n') }

  @NeedsIndex.Full
  void "test after new editing prefix back and forth when sometimes there are expected type suggestions and sometimes not"() {
    myFixture.addClass("class Super {}")
    myFixture.addClass("class Sub extends Super {}")
    myFixture.addClass("package foo; public class SubOther {}")
    myFixture.configureByText('a.java', "class C { Super s = new SubO<caret>x }")

    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'SubOther'
    myFixture.type('\b')
    myFixture.assertPreferredCompletionItems 0, 'Sub'
    myFixture.type('O')
    myFixture.assertPreferredCompletionItems 0, 'SubOther'
  }

  void "test correct typos"() {
    myFixture.configureByText("a.java", "class MyClass { MyCals<caret> }")
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult("class MyClass { MyClass<caret> }")
  }

  void testRemoveParenthesesWhenReplacingEmptyCallWithConstant() {
    doTest('\t')
  }

  void testNoCallsAfterAnnotationInCodeBlock() { doTest() }
  
  void testExtendsAfterEnum() {
    myFixture.configureByText("a.java", "enum X ex<caret>") // should not complete
    myFixture.completeBasic()
    myFixture.checkResult("enum X ex")
  }

  @NeedsIndex.Full
  void testAddImportWhenCompletingInnerAfterNew() {
    myFixture.addClass("package p; public class Outer { public static class Inner {} }")
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('Inner') })
    checkResult()
  }

  void "test completing qualified class name"() {
    myFixture.configureByText("a.java", "class C implements java.util.Li<caret>")
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems(0, 'List')
    myFixture.type('\n')
    myFixture.checkResult("class C implements java.util.List<caret>")
  }

  @NeedsIndex.ForStandardLibrary
  void "test suggest Object methods when super is unresolved"() {
    def checkGetClassPresent = { String text ->
      myFixture.configureByText("a.java", text)
      myFixture.completeBasic()
      myFixture.assertPreferredCompletionItems 0, 'getClass'
    }
    checkGetClassPresent("class C extends Unresolved {{ getCl<caret>x }}")
    checkGetClassPresent("class C implements Unresolved {{ getCl<caret>x }}")
    checkGetClassPresent("class C extends Unresolved implements Runnable {{ getCl<caret>x }}")
    checkGetClassPresent("class C extends Unresolved1 implements Unresolved2 {{ getCl<caret>x }}")
  }

  void testSuggestInverseOfDefaultAnnoParamValueForBoolean() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems(0, 'smth = true', 'value = false')

    def smthDefault = myItems.find { it.lookupString == 'smth = false' }
    def presentation = renderElement(smthDefault)
    assert presentation.tailText == ' (default)'
    assert presentation.tailFragments[0].grayed

    myFixture.type('\n')
    checkResult()
  }

  void testCaseColonAfterStringConstant() { doTest() }

  void testOneElementArray() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'aaa', 'aaa[0]'
    selectItem(myItems[1])
    checkResult()
  }

  void testSuggestChainsOfExpectedType() {
    configure()
    myFixture.assertPreferredCompletionItems 0, 'bar', 'bar().getGoo'
    selectItem(myItems[1])
    checkResult()
  }

  void testTopLevelPublicClass() { doTest() }
  void testTopLevelPublicClassIdentifierExists() { doTest() }
  void testTopLevelPublicClassBraceExists() { doTest() }

  void testPerformanceWithManyNonMatchingDeclarations() {
    def importedNumbers = 0..<10
    for (i in (importedNumbers)) {
      myFixture.addClass("class Foo$i {\n" +
                         (0..100).collect { "static int FOO$it = 3;\n" }.join("") +
                         "}")
    }
    String text = importedNumbers.collect { "import static Foo${it}.*;\n" }.join("") +
                  "class C {\n" +
                  (0..100).collect { "String method$it() {}\n" } +
                  "{ " +
                  "int localVariable = 2;\n" +
                  "localV<caret>x }" +
                  "}"
    myFixture.configureByText("a.java", text)
    PlatformTestUtil.startPerformanceTest(name, 300, {
      assert myFixture.completeBasic().length == 1
    }).setup {
      lookup?.hideLookup(true)
      myFixture.type("\bV")
      psiManager.dropPsiCaches()
      assert !lookup
    }.assertTiming()
  }

  void "test performance with many matching statically-imported declarations"() {
    def fieldCount = 7000

    myFixture.addClass("interface Constants {" +
            (0..<fieldCount).collect { "String field$it = \"x\";\n" } +
    "}")
    myFixture.configureByText("a.java", "import static Constants.*; class C { { field<caret>x } }")
    PlatformTestUtil.startPerformanceTest(name, 10_000, {
      assert myFixture.completeBasic().length > 100
    }).setup {
      lookup?.hideLookup(true)
      myFixture.type("\bd")
      psiManager.dropPsiCaches()
      assert !lookup
    }.assertTiming()
  }

  void testNoExceptionsWhenCompletingInapplicableClassNameAfterNew() { doTest('\n') }

  @NeedsIndex.ForStandardLibrary
  void "test type cast completion"() {
    myFixture.configureByText("a.java", "class X { StringBuilder[] s = (Stri<caret>)}")
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult("class X { StringBuilder[] s = (StringBuilder[]) <caret>}")
  }

  @NeedsIndex.ForStandardLibrary
  void "test type cast completion no closing parenthesis"() {
    myFixture.configureByText("a.java", "class X { StringBuilder[] s = (Stri<caret>}")
    myFixture.completeBasic()
    myFixture.type('\n')
    myFixture.checkResult("class X { StringBuilder[] s = (StringBuilder[]) <caret>}")
  }

  @NeedsIndex.ForStandardLibrary
  void "test type cast completion generic"() {
    myFixture.configureByText("a.java", "import java.util.*; class X { Map<String, Object> getMap() { return (Ma<caret>)}}")
    def elements = myFixture.completeBasic()
    assert elements.length > 0
    LookupElementPresentation presentation = renderElement(elements[0])
    assert presentation.getItemText() == "(Map<String, Object>)"
    myFixture.type('\n')
    myFixture.checkResult("import java.util.*; class X { Map<String, Object> getMap() { return (Map<String, Object>) <caret>}}")
  }

  @NeedsIndex.ForStandardLibrary
  void "test no extra space after modifier"() {
    myFixture.configureByText("a.java", "import java.util.*; class X { prot<caret> @NotNull String foo() {}}")
    myFixture.completeBasic()
    myFixture.checkResult("import java.util.*; class X { protected<caret> @NotNull String foo() {}}")
  }

  @NeedsIndex.ForStandardLibrary
  void "test suggest UTF8 Charset"() {
    myFixture.configureByText("a.java", "import java.nio.charset.Charset; class X { Charset test() {return U<caret>;}}")
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems(0, "StandardCharsets.UTF_8", "StandardCharsets.US_ASCII",
                                             "StandardCharsets.UTF_16", "StandardCharsets.UTF_16BE", "StandardCharsets.UTF_16LE")
    myFixture.type('\n')
    myFixture.checkResult("import java.nio.charset.Charset;\n" +
                          "import java.nio.charset.StandardCharsets;\n" +
                          "\n" +
                          "class X { Charset test() {return StandardCharsets.UTF_8;}}")
  }

  @NeedsIndex.ForStandardLibrary
  void "test static fields in enum initializer"() {
    myFixture.configureByText("a.java", "enum MyEnum {\n" +
                                        "    A, B, C, D, E, F;\n" +
                                        "    static int myEnumField;\n" +
                                        "    static int myEnumField2;\n" +
                                        "    static final int myEnumField3 = 10;\n" +
                                        "    static final String myEnumField4 = \"\";\n" +
                                        "    {\n" +
                                        "        System.out.println(myE<caret>);\n" +
                                        "    }\n" +
                                        "}");
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ["myEnumField3", "myEnumField4"]
  }

  void "test qualified outer class name"() {
    myFixture.configureByText("a.java", "class A {\n" +
                                        "    private static final long sss = 0L;\n" +
                                        "    static class B {\n" +
                                        "        private static final long sss = 0L;\n" +
                                        "        {\n" +
                                        "            <caret>int i = 0;\n" +
                                        "        }\n" +
                                        "    }\n" +
                                        "}\n")
    myFixture.completeBasic()
    assert myFixture.getLookupElementStrings().stream().filter({ it.contains("sss") }).collect(Collectors.toList()) == 
           ["A.sss", "sss"]
  }

  @NeedsIndex.ForStandardLibrary
  void "test private constructor"() {
    myFixture.configureByText("A.java", "class A {{new Syst<caret>}}")
    myFixture.completeBasic()
    assert ContainerUtil.filter(myFixture.getLookupElementStrings(), {it.startsWith("S")}) == ['System.Logger', 'System.LoggerFinder']
  }
  
  @NeedsIndex.SmartMode // looks like augments don't work in dumb mode
  void "test member as Java keyword"() {
    ServiceContainerUtil.registerExtension(ApplicationManager.getApplication(), PsiAugmentProvider.EP_NAME, new PsiAugmentProvider() {
      @Override
      protected <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element,
                                                               @NotNull Class<Psi> type,
                                                               @Nullable String nameHint) {
        if (element instanceof PsiExtensibleClass && element.getName() == "A" && type == PsiMethod.class) {
          PsiMethod method1 = new LightMethodBuilder(getPsiManager(), "default").setMethodReturnType(PsiType.VOID)
          PsiMethod method2 = new LightMethodBuilder(getPsiManager(), "define").setMethodReturnType(PsiType.VOID)
          return List.of(type.cast(method1), type.cast(method2))
        }
        return Collections.emptyList()
      }
    }, getTestRootDisposable());
    myFixture.configureByText("A.java", "class A {\n  void test() {\n    Runnable r = A::def<caret>\n  }\n}")
    myFixture.completeBasic()
    myFixture.checkResult("class A {\n" +
                          "  void test() {\n" +
                          "    Runnable r = A::define;\n" +
                          "  }\n" +
                          "}")
  }

  @NeedsIndex.ForStandardLibrary
  void "test no final library classes in extends"() {
    myFixture.configureByText("X.java", "class StriFoo{}final class StriBar{}class X extends Stri<caret>")
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == [
      "StriFoo", // non-final project class
      "StringIndexOutOfBoundsException", "StringTokenizer", "StringConcatException", "StringReader", "StringWriter", // non-final library classes
      "StriBar", // final project class (red)
      "StringBufferInputStream"] // deprecated library class
  }

  @NeedsIndex.ForStandardLibrary
  void "test primitive type after annotation"() {
    myFixture.configureByText("X.java", "class C {\n" +
                                        "  void m() {\n" +
                                        "    @SuppressWarnings(\"x\") boo<caret>\n" +
                                        "  }\n" +
                                        "}")
    myFixture.completeBasic()
    myFixture.checkResult("class C {\n" +
                          "  void m() {\n" +
                          "    @SuppressWarnings(\"x\") boolean\n" +
                          "  }\n" +
                          "}")
  }

  void testAfterTry() {
    myFixture.configureByText("Test.java", "class X{X() {try {}<caret>}}");
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['catch', 'finally']
  }

  @NeedsIndex.ForStandardLibrary
  void testInsertNullable() {
    myFixture.configureByText("Test.java", "class X {Stri<caret>}")
    myFixture.completeBasic()
    myFixture.type('?')
    myFixture.checkResult("import org.jetbrains.annotations.Nullable;\n" +
                          "\n" +
                          "class X {\n" +
                          "    @Nullable String\n" +
                          "}")
  }

  @NeedsIndex.ForStandardLibrary
  void testInsertNotNull() {
    myFixture.configureByText("Test.java", "class X {Stri<caret>}")
    myFixture.completeBasic()
    myFixture.type('!')
    myFixture.checkResult("import org.jetbrains.annotations.NotNull;\n" +
                          "\n" +
                          "class X {\n" +
                          "    @NotNull String\n" +
                          "}")
  }

  @NeedsIndex.Full
  void testSuperClassFieldShadowsParameter() {
    myFixture.configureByText("Test.java", "class Test {\n" +
                                           "  static class X {\n" +
                                           "    int variable;\n" +
                                           "  }\n" +
                                           "  \n" +
                                           "  void test(long variable) {\n" +
                                           "    new X() {\n" +
                                           "      double myDouble = vari<caret>\n" +
                                           "    };\n" +
                                           "  }\n" +
                                           "}")
    def lookupElements = myFixture.completeBasic()
    assert lookupElements == null
    myFixture.checkResult("class Test {\n" +
                          "  static class X {\n" +
                          "    int variable;\n" +
                          "  }\n" +
                          "  \n" +
                          "  void test(long variable) {\n" +
                          "    new X() {\n" +
                          "      double myDouble = variable\n" +
                          "    };\n" +
                          "  }\n" +
                          "}")
  }

  @NeedsIndex.Full
  void testVariableNameByTypeName() {
    myFixture.configureByText("Test.java", "class DemoEntity {} class Test {DemoEntity <caret>}")
    myFixture.completeBasic()
    assert myFixture.getLookupElementStrings() == ["demoEntity", "demo", "entity"]
  }

  @NeedsIndex.Full
  void testCompleteByEqualsAssignment() {
    CodeInsightSettings.instance.setSelectAutopopupSuggestionsByChars(true)
    myFixture.configureByText("Test.java", "public class Test {\n" +
                                         "  public static void main(final String[] args) {\n" +
                                         "    Test test = new Test();\n" +
                                         "    Test test2 = new Test();\n" +
                                         "    tes<caret>\n" +
                                         "  }\n" +
                                         "}")
    myFixture.completeBasic()
    myFixture.type('=')
    myFixture.checkResult("public class Test {\n" +
                          "  public static void main(final String[] args) {\n" +
                          "    Test test = new Test();\n" +
                          "    Test test2 = new Test();\n" +
                          "    test = <caret>\n" +
                          "  }\n" +
                          "}")
  }

  @NeedsIndex.Full
  void testCompleteByEqualsDeclaration() {
    CodeInsightSettings.instance.setSelectAutopopupSuggestionsByChars(true)
    CodeInsightSettings.instance.AUTOCOMPLETE_ON_CODE_COMPLETION = false
    myFixture.configureByText("Test.java", "public class Test {\n" +
                                         "  public static void main(final String[] args) {\n" +
                                         "    String str<caret>\n" +
                                         "  }\n" +
                                         "}")
    myFixture.completeBasic()
    myFixture.type('=')
    myFixture.checkResult("public class Test {\n" +
                          "  public static void main(final String[] args) {\n" +
                          "    String string = <caret>\n" +
                          "  }\n" +
                          "}")
  }

  @NeedsIndex.Full
  void testCompleteByEquals() {
    CodeInsightSettings.instance.setSelectAutopopupSuggestionsByChars(true)
    myFixture.configureByText("Test.java", "public class Test {\n" +
                                         "  public static void main(final String[] args) {\n" +
                                         "    final Test test = new Test();\n" +
                                         "    if (test.get<caret>)\n" +
                                         "  }\n" +
                                         "\n" +
                                         "  public String getFoo() {\n" +
                                         "    return \"\";\n" +
                                         "  }\n" +
                                         "}")
    myFixture.completeBasic()
    myFixture.type('=')
    myFixture.checkResult("public class Test {\n" +
                          "  public static void main(final String[] args) {\n" +
                          "    final Test test = new Test();\n" +
                          "    if (test.getFoo()=)\n" +
                          "  }\n" +
                          "\n" +
                          "  public String getFoo() {\n" +
                          "    return \"\";\n" +
                          "  }\n" +
                          "}")
  }

  @NeedsIndex.ForStandardLibrary
  void testSystemOutNoInitializer() {
    myFixture.configureByText("System.java", "package java.lang;\n" +
                                             "public class System {\n" +
                                             "public static final PrintStream out = null;\n" +
                                             "public static void setOut(PrintStream out) {}\n" +
                                             "void test() {System.o<caret>}\n" +
                                             "}")
    def elements = myFixture.completeBasic()
    assert elements.length > 0
    def element = elements[0]
    assert element.lookupString == "out"
    LookupElementPresentation presentation = renderElement(element)
    assert presentation.tailText == null
  }

  @NeedsIndex.ForStandardLibrary(reason = "Control flow analysis needs standard library and necessary to provide variable initializer")
  void testLocalFromTryBlock() {
    myFixture.configureByText("Test.java", "class Test {\n" +
                                           "  void test() {\n" +
                                           "    try {\n" +
                                           "      int myvar = foo();\n" +
                                           "    }\n" +
                                           "    catch (RuntimeException ex) {\n" +
                                           "    }\n" +
                                           "    my<caret>\n" +
                                           "  }\n" +
                                           "\n" +
                                           "  native int foo();\n" +
                                           "}")
    myFixture.completeBasic()
    myFixture.checkResult("class Test {\n" +
                          "  void test() {\n" +
                          "      int myvar = 0;\n" +
                          "      try {\n" +
                          "          myvar = foo();\n" +
                          "      } catch (RuntimeException ex) {\n" +
                          "      }\n" +
                          "      myvar\n" +
                          "  }\n" +
                          "\n" +
                          "  native int foo();\n" +
                          "}")
  }

  void testLocalFromAnotherIfBranch() {
    myFixture.configureByText("Test.java", "class Test {\n" +
                                           "  void test(int x) {\n" +
                                           "    if (x > 0) {\n" +
                                           "      String result = \"positive\";\n" +
                                           "    } else if (x < 0) {\n" +
                                           "      re<caret>\n" +
                                           "    }\n" +
                                           "  }\n" +
                                           "}")
    myFixture.completeBasic()
    assert myFixture.lookupElements[0].lookupString == "return"
    def element = myFixture.lookupElements[1]
    assert element.lookupString == "result"
    LookupElementPresentation presentation = renderElement(element)
    assert presentation.tailText == " (from if-then block)"
    selectItem(element)
    myFixture.checkResult("class Test {\n" +
                          "  void test(int x) {\n" +
                          "      String result = null;\n" +
                          "      if (x > 0) {\n" +
                          "          result = \"positive\";\n" +
                          "      } else if (x < 0) {\n" +
                          "          result\n" +
                          "      }\n" +
                          "  }\n" +
                          "}")
  }

  void testVariableIntoScopeInAnnotation() {
    String source = "public class Demo {\n" +
                    "    public static void main(String[] args) {\n" +
                    "        @SuppressWarnings(<caret>)\n" +
                    "        final String code = \"println('Hello world')\";\n" +
                    "    }\n" +
                    "}"
    myFixture.configureByText("Test.java", source)
    myFixture.complete(CompletionType.SMART)
    assert myFixture.lookupElementStrings == []
    myFixture.checkResult(source)
  }

  void testLookupUpDownActions() {
    myFixture.configureByText("Test.java", "class Test {<caret>}")
    myFixture.completeBasic() // 'abstract' selected
    myFixture.assertPreferredCompletionItems(0, "abstract", "boolean", "byte", "char", "class")
    myFixture.performEditorAction("EditorLookupSelectionDown") // 'boolean' selected
    myFixture.performEditorAction("EditorLookupSelectionDown") // 'byte' selected
    myFixture.performEditorAction("EditorLookupSelectionUp") // 'boolean' selected
    myFixture.type('\n')
    myFixture.checkResult("class Test {boolean}")
  }

  void testPinyinMatcher() {
    myFixture.configureByText("Test.java", "class Test {int get\u4F60\u597D() {return 0;} void test() {int \u4F60\u597D = 1;nh<caret>}}")
    myFixture.completeBasic()
    assert myFixture.getLookupElementStrings() == ['\u4F60\u597D', 'get\u4F60\u597D']
    myFixture.type('\n')
    myFixture.checkResult("class Test {int get\u4F60\u597D() {return 0;} void test() {int \u4F60\u597D = 1;\u4F60\u597D}}")
  }

  void testPinyinMatcher2() {
    myFixture.configureByText("Test.java", "class Test {static void test() {int \u89D2\u8272 = 3;gj<caret>}}")
    myFixture.completeBasic()
    assert myFixture.getLookupElementStrings() == []
    myFixture.type('\b')
    myFixture.completeBasic()
    myFixture.checkResult("class Test {static void test() {int \u89D2\u8272 = 3;\u89D2\u8272}}")
  }
}
