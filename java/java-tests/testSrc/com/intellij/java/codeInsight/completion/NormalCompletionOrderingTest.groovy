// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.

package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.JavaPsiClassReferenceElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.ide.ui.UISettings
import com.intellij.internal.DumpLookupElementWeights
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.NeedsIndex
import com.intellij.ui.JBColor

import static com.intellij.java.codeInsight.completion.NormalCompletionTestCase.renderElement

class NormalCompletionOrderingTest extends CompletionSortingTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/normalSorting"

  NormalCompletionOrderingTest() {
    super(CompletionType.BASIC)
  }

  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH
  }

  void testDontPreferRecursiveMethod() throws Throwable {
    checkPreferredItems(0, "registrar", "register")
  }

  void testDontPreferRecursiveMethod2() throws Throwable {
    checkPreferredItems(0, "return", "register")
  }

  void testDelegatingConstructorCall() {
    checkPreferredItems 0, 'element'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferAnnotationMethods() throws Throwable {
    checkPreferredItems(0, "name", "value", "String", "Foo", "Anno")
  }

  void testPreferSuperMethods() throws Throwable {
    checkPreferredItems(0, "foo", "bar")
  }

  @NeedsIndex.ForStandardLibrary
  void testSubstringVsSubSequence() throws Throwable {
    checkPreferredItems(0, "substring", "substring", "subSequence")
  }

  @NeedsIndex.ForStandardLibrary
  void testReturnF() throws Throwable {
    checkPreferredItems(0, "false", "float", "finalize")
  }

  void testPreferDefaultTypeToExpected() throws Throwable {
    checkPreferredItems(0, "getName", "getNameIdentifier")
  }

  @NeedsIndex.SmartMode(reason = "Smart completion in dumb mode is not supported for html")
  void testShorterPrefixesGoFirst() throws Throwable {
    invokeCompletion(getTestName(false) + ".html")
    assertPreferredItems(0, "p", "param", "pre")
    incUseCount(2)
    assertPreferredItems(0, "p", "pre", "param")
  }

  void testUppercaseMatters2() throws Throwable {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.ALL)
    checkPreferredItems(0, "classLoader", "classLoader2")
  }

  void testShorterShouldBePreselected() throws Throwable {
    checkPreferredItems(0, "foo", "fooLongButOfDefaultType")
  }

  void testGenericMethodsWithBoundParametersAreStillBetterThanClassLiteral() throws Throwable {
    checkPreferredItems(0, "getService", "getService", "class")
  }

  @NeedsIndex.ForStandardLibrary
  void testGenericityDoesNotMatterWhenNoTypeIsExpected() {
    checkPreferredItems 0, "generic", "nonGeneric", "clone", "equals"
  }

  void testClassStaticMembersInVoidContext() throws Throwable {
    checkPreferredItems(0, "booleanMethod", "voidMethod", "AN_OBJECT", "BOOLEAN", "class")
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferClassLiteralWhenClassIsExpected() {
    checkPreferredItems(0, "class")
  }

  @NeedsIndex.ForStandardLibrary
  void testJComponentInstanceMembers() throws Throwable {
    checkPreferredItems(0, "getAccessibleContext", "getUI", "getUIClassID")
  }

  void testClassStaticMembersInBooleanContext() throws Throwable {
    final String path = getTestName(false) + ".java"
    myFixture.configureByFile(path)
    myFixture.complete(CompletionType.BASIC, 2)
    assertPreferredItems(0, "booleanMethod", "BOOLEAN", "AN_OBJECT", "class", "Inner", "voidMethod")
  }

  void testDispreferDeclared() throws Throwable {
    checkPreferredItems(0, "aabbb", "aaa")
  }

  @NeedsIndex.Full
  void testDispreferImpls() throws Throwable {
    myFixture.addClass("package foo; public class Xxx {}")
    configureSecondCompletion()
    assertPreferredItems(0, "Xxx", "XxxEx", "XxxImpl", "Xxy")
  }

  @NeedsIndex.Full
  void testPreferOwnInnerClasses() throws Throwable {
    checkPreferredItems(0, "YyyXxx", "YyyZzz")
  }

  @NeedsIndex.Full
  void testPreferTopLevelClasses() throws Throwable {
    configureSecondCompletion()
    assertPreferredItems(0, "XxxYyy", "XxzYyy")
  }

  private void configureSecondCompletion() {
    configureNoCompletion(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC, 2)
  }

  @NeedsIndex.Full
  void testImplsAfterNew() {
    myFixture.addClass("package foo; public interface Xxx {}")
    configureSecondCompletion()
    assertPreferredItems(0, "XxxImpl", "Xxx")
  }

  @NeedsIndex.ForStandardLibrary
  void testAfterThrowNew() {
    checkPreferredItems(0, "Exception", "RuntimeException")
  }

  @NeedsIndex.Full
  void testPreferLessHumps() throws Throwable {
    myFixture.addClass("package foo; public interface XaYa {}")
    myFixture.addClass("package foo; public interface XyYa {}")
    configureSecondCompletion()
    assertPreferredItems(0, "XaYa", "XaYaEx", "XaYaImpl", "XyYa", "XyYaXa")
  }

  void testPreferLessParameters() throws Throwable {
    checkPreferredItems(0, "foo", "foo", "foo", "fox")
    final List<LookupElement> items = getLookup().getItems()
    assertEquals(0, ((PsiMethod)items.get(0).getObject()).getParameterList().getParametersCount())
    assertEquals(1, ((PsiMethod)items.get(1).getObject()).getParameterList().getParametersCount())
    assertEquals(2, ((PsiMethod)items.get(2).getObject()).getParameterList().getParametersCount())
  }

  @NeedsIndex.Full
  void testStatsForClassNameInExpression() throws Throwable {
    myFixture.addClass("package foo; public interface FooBar {}")
    myFixture.addClass("package foo; public interface FooBee {}")

    invokeCompletion(getTestName(false) + ".java")
    configureSecondCompletion()
    incUseCount(1)
    assertPreferredItems(0, "FooBee", "FooBar")
  }

  @NeedsIndex.ForStandardLibrary
  void testDispreferFinalize() throws Throwable {
    checkPreferredItems(0, "final", "finalize")
  }

  void testPreferNewExpectedInner() throws Throwable {
    checkPreferredItems(0, "Foooo.Bar", "Foooo")

    /*final LookupElementPresentation presentation = new LookupElementPresentation();
    getLookup().getItems().get(0).renderElement(presentation);
    assertEquals("Foooo.Bar", presentation.getItemText());*/
  }

  @NeedsIndex.ForStandardLibrary
  void testDeclaredMembersGoFirst() throws Exception {
    invokeCompletion(getTestName(false) + ".java")
    assertStringItems("fromThis", "overridden", "fromSuper", "equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait",
                      "wait", "wait")
  }

  @NeedsIndex.ForStandardLibrary
  void testLocalVarsOverMethods() {
    checkPreferredItems(0, "value", "isValidateRoot", "isValid", "validate", "validateTree")
  }

  void testCurrentClassBest() {
    checkPreferredItems(0, "XcodeProjectTemplate", "XcodeConfigurable")
  }

  @NeedsIndex.Full
  void testFqnStats() {
    myFixture.addClass("public interface Baaaaaaar {}")
    myFixture.addClass("package boo; public interface Baaaaaaar {}")
    myFixture.addClass("package zoo; public interface Baaaaaaar {}")

    configureSecondCompletion()

    assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.items[0]).qualifiedName)
    assertEquals("boo.Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.items[1]).qualifiedName)
    assertEquals("zoo.Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.items[2]).qualifiedName)
    incUseCount(2)

    assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.items[0]).qualifiedName) // same package
    assertEquals("zoo.Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.items[1]).qualifiedName)
    assertEquals("boo.Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.items[2]).qualifiedName)
  }

  @NeedsIndex.ForStandardLibrary
  void testSkipLifted() {
    checkPreferredItems(0, "hashCodeMine", "hashCode")
  }

  @NeedsIndex.ForStandardLibrary
  void testDispreferInnerClasses() {
    checkPreferredItems(0) //no chosen items
    assertFalse(getLookup().getItems().get(0).getObject() instanceof PsiClass)
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferSameNamedMethods() {
    checkPreferredItems(0, "foo", "boo", "doo", "hashCode")
  }

  void testPreferInterfacesInImplements() {
    checkPreferredItems(0, "XFooIntf", "XFoo", "XFooClass")
    assert renderElement(lookup.items[0]).itemTextForeground == JBColor.foreground()
    assert renderElement(lookup.items[1]).itemTextForeground == JBColor.RED
    assert renderElement(lookup.items[2]).itemTextForeground == JBColor.RED
  }

  void testPreferClassesInExtends() {
    checkPreferredItems(0, "FooClass", "Foo_Intf")
  }

  void testPreferClassStaticMembers() {
    checkPreferredItems(0, "Zoo.A", "Zoo", "Zoo.B", "Zoo.C", "Zoo.D", "Zoo.E", "Zoo.F", "Zoo.G", "Zoo.H")
  }

  void testPreferFinallyToFinal() {
    checkPreferredItems(0, "finally", "final")
  }

  void testPreferReturn() {
    checkPreferredItems(0, "return", "rLocal", "rParam", "rMethod")
  }

  void testPreferReturnBeforeExpression() {
    checkPreferredItems(0, "return", "rLocal", "rParam", "rMethod")
  }

  void testPreferReturnBeforeExpression2() {
    checkPreferredItems(0, "return", "retainAll")
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferReturnInSingleStatementPlace() {
    checkPreferredItems 0, "return", "registerKeyboardAction"
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferContinueInsideLoops() {
    checkPreferredItems 0, "continue", "color", "computeVisibleRect"
  }

  void testPreferModifiers() {
    checkPreferredItems(0, "private", "protected", "public")
  }

  void testPreferEnumConstants() {
    checkPreferredItems(0, "MyEnum.bar", "MyEnum", "MyEnum.foo")
  }

  @NeedsIndex.Full
  void testPreferExpectedEnumConstantsDespiteStats() {
    checkPreferredItems 0, "const1", "const2", "constx1", "constx2"
    myFixture.lookup.currentItem = myFixture.lookupElements[2]

    myFixture.type('\n);\nmethodX(cons')
    myFixture.completeBasic()
    assertPreferredItems 0, 'constx1', 'constx2', 'const1', 'const2'
  }

  void testPreferExpectedEnumConstantsInComparison() {
    checkPreferredItems 0, 'MyEnum.const1', 'MyEnum', 'MyEnum.const2'
    incUseCount(myFixture.lookupElementStrings.indexOf('String')) // select some unrelated class
    assertPreferredItems 0, 'MyEnum.const1', 'MyEnum', 'MyEnum.const2'
  }

  void testPreferElse() {
    checkPreferredItems(0, "else", "element")
  }

  void testPreferMoreMatching() {
    checkPreferredItems(0, "FooOCSomething", "FooObjectCollector")
  }

  void testPreferSamePackageOverImported() {
    myFixture.addClass("package bar; public class Bar1 {}")
    myFixture.addClass("package bar; public class Bar2 {}")
    myFixture.addClass("package bar; public class Bar3 {}")
    myFixture.addClass("package bar; public class Bar4 {}")
    myFixture.addClass("class Bar9 {}")
    myFixture.addClass("package doo; public class Bar0 {}")

    checkPreferredItems(0, "Bar9", "Bar1", "Bar2", "Bar3", "Bar4")
  }

  @NeedsIndex.Full
  void testPreselectMostRelevantInTheMiddleAlpha() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true)

    myFixture.addClass("package foo; public class ELXaaaaaaaaaaaaaaaaaaaa {}")
    invokeCompletion(getTestName(false) + ".java")
    myFixture.completeBasic()
    LookupImpl lookup = getLookup()
    assertPreferredItems(lookup.getList().getSelectedIndex())
    assertEquals("ELXaaaaaaaaaaaaaaaaaaaa", lookup.getItems().get(0).getLookupString())
    assertEquals("ELXEMENT_A", lookup.getCurrentItem().getLookupString())
  }

  void testReallyAlphaSorting() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true)

    invokeCompletion(getTestName(false) + ".java")
    assert myFixture.lookupElementStrings.sort() == myFixture.lookupElementStrings
  }

  @NeedsIndex.Full
  void testAlphaSortPackages() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true)

    def pkgs = ['bar', 'foo', 'goo', 'roo', 'zoo']
    for (s in pkgs) {
      myFixture.addClass("package $s; public class Foox {}")
    }
    invokeCompletion(getTestName(false) + ".java")
    for (i in 0..<pkgs.size()) {
      assert renderElement(myFixture.lookupElements[i]).tailText?.contains(pkgs[i])
    }
  }

  void testAlphaSortingStartMatchesFirst() {
    UISettings.getInstance().setSortLookupElementsLexicographically(true)
    checkPreferredItems 0, 'xxbar', 'xxfoo', 'xxgoo', 'barxx', 'fooxx', 'gooxx'
  }

  @NeedsIndex.Full
  void testSortSameNamedVariantsByProximity() {
    myFixture.addClass("public class Bar {}")
    for (int i = 0; i < 10; i++) {
      myFixture.addClass("public class Bar" + i + " {}")
      myFixture.addClass("public class Bar" + i + "Colleague {}")
    }
    myFixture.addClass("package bar; public class Bar {}")
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC, 2)
    assertPreferredItems(0, "Bar", "Bar")
    List<LookupElement> items = getLookup().getItems()
    assertEquals(((JavaPsiClassReferenceElement)items.get(0)).getQualifiedName(), "Bar")
    assertEquals(((JavaPsiClassReferenceElement)items.get(1)).getQualifiedName(), "bar.Bar")
  }

  void testCaseInsensitivePrefixMatch() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE)
    checkPreferredItems(1, "Foo", "foo1", "foo2")
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferKeywordsToVoidMethodsInExpectedTypeContext() {
    checkPreferredItems 0, 'noo', 'new', 'null', 'noo2', 'new File', 'new File', 'new File', 'new File', 'clone', 'toString', 'notify', 'notifyAll'
  }

  void testPreferBetterMatchingConstantToMethods() {
    checkPreferredItems 0, 'serial', 'superExpressionInIllegalContext'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferApplicableAnnotations() throws Throwable {
    myFixture.addClass '''
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE})
@interface TMetaAnno {}

@Target({ElementType.LOCAL_VARIABLE})
@interface TLocalAnno {}'''
    checkPreferredItems 0, 'TMetaAnno', 'Target', 'TabLayoutPolicy', 'TabPlacement'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferApplicableAnnotationsMethod() throws Throwable {
    myFixture.addClass '''
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@interface TxClassAnno {}

interface TxANotAnno {}

@Target({ElementType.METHOD})
@interface TxMethodAnno {}'''
    checkPreferredItems 0, 'TxMethodAnno', 'TxClassAnno'
    assert !('TxANotAnno' in myFixture.lookupElementStrings)
  }

  @NeedsIndex.ForStandardLibrary
  void testJComponentAddNewWithStats() throws Throwable {
    invokeCompletion("/../smartTypeSorting/JComponentAddNew.java")
    assertPreferredItems(0, "FooBean3", "JComponent", "Component")
    incUseCount(2) //Component
    assertPreferredItems(0, "Component", "FooBean3", "JComponent")
  }

  void testDispreferReturnBeforeStatement() {
    checkPreferredItems 0, 'reaction', 'rezet', 'return'
  }

  void testDispreferReturnInConstructor() {
    checkPreferredItems 0, 'reaction', 'rezet', 'return'
  }

  void testDispreferReturnInVoidMethodTopLevel() {
    checkPreferredItems 0, 'reaction', 'rezet', 'return'
  }

  void testDispreferReturnAsLoopBody() {
    checkPreferredItems 0, 'returnMethod', 'return'
  }

  @NeedsIndex.ForStandardLibrary
  void testDispreferReturnInVoidLambda() {
    checkPreferredItems 0, 'reaction', 'rezet', 'return'
  }

  @NeedsIndex.ForStandardLibrary
  void testDoNotPreferGetClass() {
    checkPreferredItems 0, 'get', 'getClass'
    incUseCount(1)
    assertPreferredItems 0, 'getClass', 'get'
    incUseCount(1)
    assertPreferredItems 0, 'get', 'getClass'
  }

  @NeedsIndex.ForStandardLibrary
  void testEqualsStats() {
    checkPreferredItems 0, 'equals', 'equalsIgnoreCase'
    incUseCount(1)
    assertPreferredItems 0, 'equalsIgnoreCase', 'equals'
    incUseCount(1)
    checkPreferredItems 0, 'equals', 'equalsIgnoreCase'
  }

  void testPreferClassToItsConstants() {
    checkPreferredItems 0, 'MyCalendar.FIELD_COUNT', 'MyCalendar', 'MyCalendar.AM'
  }

  @NeedsIndex.Full
  void testPreferLocalsToStaticsInSecondCompletion() {
    myFixture.addClass('public class FooZoo { public static void fooBar() {}  }')
    myFixture.addClass('public class fooAClass {}')
    configureNoCompletion(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC, 2)
    assertPreferredItems(0, 'fooy', 'foox', 'fooAClass', 'fooBar')
  }

  void testChangePreselectionOnSecondInvocation() {
    configureNoCompletion(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC)
    assertPreferredItems(0, 'fooZooGoo', 'fooZooImpl')
    myFixture.complete(CompletionType.BASIC)
    assertPreferredItems(0, 'fooZoo', 'fooZooGoo', 'fooZooImpl')
  }

  void testUnderscoresDontMakeMatchMiddle() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE)
    checkPreferredItems(0, 'fooBar', '_fooBar', 'FooBar')
  }

  void testDispreferUnderscoredCaseMatch() {
    checkPreferredItems(0, 'fooBar', '__foo_bar')
  }

  void testStatisticsMattersOnNextCompletion() {
    configureByFile(getTestName(false) + ".java")
    myFixture.completeBasic()
    assert lookup
    assert lookup.currentItem.lookupString != 'JComponent'
    myFixture.type('ponent c;\nJCom')
    myFixture.completeBasic()
    assert lookup
    assert lookup.currentItem.lookupString == 'JComponent'
  }

  void testPreferFieldToMethod() {
    checkPreferredItems(0, 'size', 'size')
    assert lookup.items[0].object instanceof PsiField
    assert lookup.items[1].object instanceof PsiMethod
  }

  @NeedsIndex.ForStandardLibrary
  void testPreselectLastChosen() {
    checkPreferredItems(0, 'add', 'addAll')
    for (i in 0..10) {
      incUseCount(1)
    }
    assertPreferredItems 0, 'addAll', 'add'
    incUseCount(1)
    assertPreferredItems 0, 'add', 'addAll'
  }

  void testDontPreselectLastChosenWithUnrelatedPrefix() {
    invokeCompletion(getTestName(false) + ".java")
    myFixture.type(';\nmycl')
    myFixture.completeBasic()
    assertPreferredItems 0, 'myClass', 'myExtendsClause'
  }

  void testCommonPrefixMoreImportantThanExpectedType() {
    checkPreferredItems 0, 'myStep', 'myCurrentStep'
  }

  void testStatsMoreImportantThanExpectedType() {
    invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems 0, 'getNumber', 'getNumProvider'
    lookup.currentItem = lookup.items[1]
    myFixture.type '\n);\ntest(getnu'
    myFixture.completeBasic()
    assertPreferredItems 0, 'getNumProvider', 'getNumber'
  }

  void testIfConditionStats() {
    invokeCompletion(getTestName(false) + ".java")
    myFixture.completeBasic()
    myFixture.type('cont')
    assertPreferredItems 0, 'containsAll', 'contains'
    myFixture.lookup.currentItem = myFixture.lookupElements[1]
    myFixture.type('\nc)) {\nif (set.')
    myFixture.completeBasic()
    myFixture.type('cont')
    assertPreferredItems 0, 'contains', 'containsAll'
  }

  void testDeepestSuperMethodStats() {
    invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems 0, 'addX', 'addY'
    myFixture.type('y\n;set1.ad')

    myFixture.completeBasic()
    assertPreferredItems 0, 'addY', 'addX'
    myFixture.type('x\n;set2.ad')

    myFixture.completeBasic()
    assertPreferredItems 0, 'addX', 'addY'
  }

  void testCommonPrefixMoreImportantThanKind() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE)
    checkPreferredItems(0, 'PsiElement', 'psiElement')
  }

  void testNoExpectedTypeInStringConcatenation() {
    checkPreferredItems(0, 'vx')
  }

  void testLocalVarsOverStats() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE)
    checkPreferredItems 0, 'psiElement', 'PsiElement'
    incUseCount 1
    assertPreferredItems 0, 'psiElement', 'PsiElement'
  }

  void testHonorRecency() {
    invokeCompletion(getTestName(false) + ".java")
    myFixture.completeBasic()
    myFixture.type('setou\nz.')

    myFixture.completeBasic()
    myFixture.type('set')
    assertPreferredItems 0, 'setOurText', 'setText'
    myFixture.type('te')
    assertPreferredItems 0, 'setText', 'setOurText'
    myFixture.type('\nz.')

    myFixture.completeBasic()
    myFixture.type('set')
    assertPreferredItems 0, 'setText', 'setOurText'
  }

  void testPreferString() {
    checkPreferredItems 0, 'String', 'System', 'Short'
  }

  void testAnnotationEnum() {
    checkPreferredItems 0, 'MyEnum.BAR', 'MyEnum', 'MyEnum.FOO'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferClassesOfExpectedClassType() {
    myFixture.addClass "class XException extends Exception {}"
    checkPreferredItems 0, 'XException', 'XClass', 'XIntf'
  }

  void testNoNumberValueOf() {
    checkPreferredItems 0, 'value'
  }

  void testNoBooleansInMultiplication() {
    checkPreferredItems 0, 'fact'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferAnnotationsToInterfaceKeyword() {
    checkPreferredItems 0, 'Deprecated', 'Override', 'SuppressWarnings'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferThrownExceptionsInCatch() {
    checkPreferredItems 0, 'final', 'FileNotFoundException', 'File'
  }

  void testHonorFirstLetterCase() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE)
    checkPreferredItems 0, 'posIdMap', 'PImageDecoder', 'PNGImageDecoder'
  }

  @NeedsIndex.Full
  void testGlobalStaticMemberStats() {
    configureNoCompletion(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC, 2)
    assertPreferredItems 0, 'newLinkedSet0', 'newLinkedSet1', 'newLinkedSet2'
    imitateItemSelection(lookup, 1)
    myFixture.complete(CompletionType.BASIC, 2)
    assertPreferredItems 0, 'newLinkedSet1', 'newLinkedSet0', 'newLinkedSet2'
  }

  @NeedsIndex.ForStandardLibrary
  void testStaticMemberTypes() {
    checkPreferredItems 0, 'newMap', 'newList'
  }

  @NeedsIndex.ForStandardLibrary
  void testNoStatsInSuperInvocation() {
    checkPreferredItems 0, 'put', 'putAll'

    myFixture.type('\n')
    assert myFixture.editor.document.text.contains("put")
    
    myFixture.type(');\nsuper.')
    myFixture.completeBasic()

    assertPreferredItems 0, 'get'
  }

  void testLiveTemplateOrdering() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable())
    checkPreferredItems(0, 'return')
    assert lookup.items.find { it.lookupString == 'ritar'} != null
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferLocalToExpectedTypedMethod() {
    checkPreferredItems 0, 'event', 'equals'
  }

  void testDispreferJustUsedEnumConstantsInSwitch() {
    checkPreferredItems 0, 'BAR', 'FOO', 'GOO'
    myFixture.type('\nbreak;\ncase ')

    def items = myFixture.completeBasic()
    assertPreferredItems 0, 'FOO', 'GOO', 'BAR'

    assert renderElement(items[0]).itemTextForeground == JBColor.foreground()
    assert renderElement(items.find { it.lookupString == 'BAR' }).itemTextForeground == JBColor.RED
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferValueTypesReturnedFromMethod() {
    checkPreferredItems 0, 'StringBuffer', 'String', 'Serializable', 'SomeInterface', 'SomeInterface', 'SomeOtherClass'
    assert 'SomeInterface<String>' == renderElement(myFixture.lookupElements[3]).itemText
    assert 'SomeInterface' == renderElement(myFixture.lookupElements[4]).itemText
  }

  @NeedsIndex.Full
  void testPreferCastTypesHavingSpecifiedMethod() {
    checkPreferredItems 0, 'MainClass1', 'MainClass2', 'Maa'
  }

  void testNaturalSorting() {
    checkPreferredItems 0, 'fun1', 'fun2', 'fun10'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferVarsHavingReferencedMember() {
    checkPreferredItems 0, 'xzMap', 'xaString'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferCollectionsStaticOfExpectedType() {
    checkPreferredItems 0, 'unmodifiableList', 'unmodifiableCollection'
  }

  @NeedsIndex.Full
  void testDispreferDeprecatedMethodWithUnresolvedQualifier() {
    myFixture.addClass("package foo; public class Assert { public static void assertTrue() {} }")
    myFixture.addClass("package bar; @Deprecated public class Assert { public static void assertTrue() {}; public static void assertTrue2() {} }")
    checkPreferredItems 0, 'Assert.assertTrue', 'Assert.assertTrue', 'Assert.assertTrue2'

    def p = renderElement(myFixture.lookup.items[0])
    assert p.tailText.contains('foo')
    assert !p.strikeout

    p = renderElement(myFixture.lookup.items[1])
    assert p.tailText.contains('bar')
    assert p.strikeout
  }

  void testPreferClassKeywordWhenExpectedClassType() {
    checkPreferredItems 0, 'class'
  }

  void testPreferBooleanKeywordsWhenExpectedBoolean() {
    checkPreferredItems 0, 'false', 'factory'
  }

  void testPreferBooleanKeywordsWhenExpectedBoolean2() {
    checkPreferredItems 0, 'false', 'factory'
  }

  @NeedsIndex.Full
  void testPreferExplicitlyImportedStaticMembers() {
    myFixture.addClass("""
class ContainerUtilRt {
  static void newHashSet();
  static void newHashSet2();
}
class ContainerUtil extends ContainerUtilRt {
  static void newHashSet();
  static void newHashSet3();
}
""")
    checkPreferredItems 0, 'newHashSet', 'newHashSet', 'newHashSet3', 'newHashSet2'
    assert (myFixture.lookupElements[0].psiElement as PsiMethod).containingClass.name == 'ContainerUtil'
  }

  void testPreferCatchAndFinallyAfterTry() {
    checkPreferredItems 0, 'catch', 'finally'
  }

  @NeedsIndex.Full
  void testPreselectClosestExactPrefixItem() {
    UISettings.instance.setSortLookupElementsLexicographically(true)
    myFixture.addClass 'package pack1; public class SameNamed {}'
    myFixture.addClass 'package pack2; public class SameNamed {}'
    checkPreferredItems 1, 'SameNamed', 'SameNamed'
    assert renderElement(myFixture.lookupElements[0]).tailText.contains('pack1')
    assert renderElement(myFixture.lookupElements[1]).tailText.contains('pack2')
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferExpectedMethodTypeArg() {
    checkPreferredItems 0, 'String', 'Usage'

    def typeArgOffset = myFixture.editor.caretModel.offset

    // increase usage stats of ArrayList
    LookupManager.getInstance(project).hideActiveLookup()
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
    myFixture.type('\nArrayLi')
    myFixture.completeBasic()
    myFixture.type(' l')
    myFixture.completeBasic()
    myFixture.type('\n;')
    assert myFixture.editor.document.text.contains('ArrayList list;')

    // check String is still preferred
    myFixture.editor.caretModel.moveToOffset(typeArgOffset)
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'String', "Usage"
  }

  @NeedsIndex.ForStandardLibrary
  void testMethodStatisticsPerQualifierType() {
    checkPreferredItems 0, 'charAt'
    myFixture.type('eq\n);\n')
    assert myFixture.editor.document.text.contains('equals();\n')

    myFixture.type('this.')
    myFixture.completeBasic()
    assertPreferredItems 0, 'someMethod'
  }

  @NeedsIndex.Full
  void testPreferImportedClassesAmongstSameNamed() {
    myFixture.addClass('package foo; public class String {}')

    // use foo.String
    myFixture.configureByText 'a.java', 'class Foo { Stri<caret> }'
    myFixture.completeBasic()
    myFixture.lookup.currentItem = myFixture.lookupElements.find { it.lookupString == 'String' && renderElement(it).tailText.contains('foo') }
    myFixture.type('\n')
    myFixture.checkResult 'import foo.String;\n\nclass Foo { String<caret>\n}'

    // assert non-imported String is not preselected when completing in another file
    myFixture.configureByText 'b.java', 'class Bar { Stri<caret> }'
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'String', 'String'
    assert renderElement(myFixture.lookupElements[0]).tailText.contains('java.lang')
  }

  void testClassNameStatisticsDoesntDependOnExpectedType() {
    checkPreferredItems 0, 'ConflictsUtil', 'ContainerUtil'
    myFixture.lookup.currentItem = myFixture.lookupElements[1]
    myFixture.type('\n.foo();\nlong l = ConUt')
    myFixture.completeBasic()

    assertPreferredItems 0, 'ContainerUtil', 'ConflictsUtil'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferListAddWithoutIndex() {
    checkPreferredItems 0, 'add', 'add', 'addAll', 'addAll'
    assert renderElement(myFixture.lookupElements[1]).tailText.contains('int index')
    assert renderElement(myFixture.lookupElements[3]).tailText.contains('int index')
  }

  @NeedsIndex.Full
  void testPreferExpectedTypeConstantOverSameNamedClass() {
    myFixture.addClass("package another; public class JSON {}")
    checkPreferredItems 0, 'Point.JSON', 'JSON'
  }

  void testPreferExpectedEnumConstantInAnnotationAttribute() {
    checkPreferredItems 0, 'MyEnum.bar', 'MyEnum', 'MyEnum.foo'
    def unrelatedItem = myFixture.lookupElementStrings.findIndexOf { it.contains('Throwable') }
    incUseCount(unrelatedItem)
    //nothing should change
    assertPreferredItems 0, 'MyEnum.bar', 'MyEnum', 'MyEnum.foo'
  }

  void testPreferExpectedTypeFieldOverUnexpectedLocalVariables() {
    checkPreferredItems 0, 'field', 'local'
  }

  void testPreferConflictingFieldAfterThis() {
    checkPreferredItems 0, 'text'
  }

  void testDoNotPreselectShorterDeprecatedClasses() {
    checkPreferredItems 1, 'XLong', 'XLonger'
  }

  void testSignatureBeforeStats() {
    myFixture.configureByText 'a.java', '''
class Foo { 
  void foo(int a, int b) {
    Stri<caret>
    bar();
  }
  
  void bar(int a, int b) {} 
}'''
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'String'
    myFixture.type('\n') // increase String statistics
    myFixture.type('foo;\nbar(')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'a', 'b', 'a, b'
  }

  @NeedsIndex.ForStandardLibrary
  void "test selecting static field after static method"() {
    myFixture.configureByText 'a.java', 'class Foo { { System.<caret> } }'
    myFixture.completeBasic()
    myFixture.type('ex\n2);\n') // select 'exit'
    
    myFixture.type('System.')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'exit'
    myFixture.type('ou\n;\n') // select 'out'

    myFixture.type('System.')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'out', 'exit'    
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters)")
  void testPreferTypeToGeneratedMethod() {
    checkPreferredItems 0, 'SomeClass', 'public SomeClass getZoo'
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for getters and equals())")
  void testPreferPrimitiveTypeToGeneratedMethod() {
    checkPreferredItems 0, 'boolean', 'public boolean isZoo', 'public boolean equals'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferExceptionsInCatch() {
    myFixture.configureByText 'a.java', 'class Foo { { Enu<caret> } }'
    myFixture.completeBasic()
    myFixture.type('m\n') // select 'Enum'
    myFixture.type('; try {} catch(E')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'Exception', 'Error' 
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferExceptionsInThrowsList() {
    checkPreferredItems 0, 'IllegalStateException', 'IllegalAccessException', 'IllegalArgumentException'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferExceptionsInJavadocThrows() {
    checkPreferredItems 0, 'IllegalArgumentException', 'IllegalAccessException', 'IllegalStateException'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferExpectedTypeArguments() {
    checkPreferredItems 0, 'BlaOperation'
  }

  void testPreferFinalBeforeVariable() {
    checkPreferredItems 0, 'final', 'find1'
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testDispreferMultiMethodInterfaceAfterNew() {
    checkPreferredItems 1, 'Intf', 'IntfImpl'
  }

  void testDispreferAlreadyCalledBuilderMethods() {
    checkPreferredItems 0, 'addInt', 'append', 'c', 'd', 'mayCallManyTimes', 'putLong'
  }

  void testSelectAbstractClassWithNoAbstractMethods() {
    checkPreferredItems 0, 'AbstractListener', 'Listener'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferPrintln() {
    myFixture.configureByText 'a.java', 'class Foo { { System.out.pri<caret>x } }'
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, 'println', 'print'
    myFixture.type('\t')
    myFixture.checkResult 'class Foo { { System.out.println(<caret>); } }'
  }

  void testPreferLocalArrayVariableToItsChains() {
    checkPreferredItems 0, 'arrayVariable'
  }

  @NeedsIndex.ForStandardLibrary
  void "test void method in nonvoid context"() {
    myFixture.configureByText("a.java", "class X { String getName() {return \"\";} void test() {System.out.println(this.n<caret>);}}")
    myFixture.completeBasic()
    assertStringItems "getName", "clone", "toString", "notify", "notifyAll", "finalize"
  }

  @NeedsIndex.ForStandardLibrary
  void "test void context replace"() {
    myFixture.configureByText("a.java", "class X { String getName() {return \"\";} void test() {this.n<caret>otify();}}")
    myFixture.completeBasic()
    assertStringItems "notify", "notifyAll", "getName", "clone", "toString", "finalize"
  }

  @NeedsIndex.Full
  void "test import nested classes order"() {
    myFixture.addClass("public class Cls { public static class TestImport {}}")
    myFixture.addClass("public class Cls2 { public static class TestImport {}}")
    myFixture.addClass("package demo;public class TestImport {}")
    myFixture.addClass("public class TestImport {}")
    myFixture.configureByText("a.java", "import demo.*;import static Cls2.*; class Test {TestIm<caret>}")
    myFixture.completeBasic()
    def elements = myFixture.lookupElements
    assert elements.length == 4
    def weights = DumpLookupElementWeights.getLookupElementWeights(lookup, false)
    assert elements[0].as(JavaPsiClassReferenceElement).getQualifiedName() == "TestImport"
    assert weights[0].contains("explicitlyImported=CLASS_DECLARED_IN_SAME_PACKAGE_TOP_LEVEL,") // same package
    assert elements[1].as(JavaPsiClassReferenceElement).getQualifiedName() == "demo.TestImport"
    assert weights[1].contains("explicitlyImported=CLASS_ON_DEMAND_TOP_LEVEL,") // on-demand import
    assert elements[2].as(JavaPsiClassReferenceElement).getQualifiedName() == "Cls2.TestImport"
    assert weights[2].contains("explicitlyImported=CLASS_ON_DEMAND_NESTED,") // same package, nested class imported
    assert elements[3].as(JavaPsiClassReferenceElement).getQualifiedName() == "Cls.TestImport"
    assert weights[3].contains("explicitlyImported=CLASS_DECLARED_IN_SAME_PACKAGE_NESTED,") // same package but nested class not imported
  }

  @NeedsIndex.Full
  void "test discourage experimental"() {
    myFixture.addClass("package org.jetbrains.annotations;public class ApiStatus{public @interface Experimental {}}");
    myFixture.addClass("class Cls {@org.jetbrains.annotations.ApiStatus.Experimental public void methodA() {} public void methodB() {}}")
    myFixture.configureByText("a.java", "class Test {void t(Cls cls) {cls.me<caret>}}")
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ["methodB", "methodA"]
  }
}
