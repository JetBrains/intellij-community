/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.ui.JBColor

public class NormalCompletionOrderingTest extends CompletionSortingTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/normalSorting";

  public NormalCompletionOrderingTest() {
    super(CompletionType.BASIC);
  }

  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH;
  }

  public void testDontPreferRecursiveMethod() throws Throwable {
    checkPreferredItems(0, "registrar", "register");
  }

  public void testDontPreferRecursiveMethod2() throws Throwable {
    checkPreferredItems(0, "return", "register");
  }

  public void testDelegatingConstructorCall() {
    checkPreferredItems 0, 'element', 'equals'
  }

  public void testPreferAnnotationMethods() throws Throwable {
    checkPreferredItems(0, "name", "value", "Foo", "Anno");
  }

  public void testPreferSuperMethods() throws Throwable {
    checkPreferredItems(0, "foo", "bar");
  }

  public void testSubstringVsSubSequence() throws Throwable {
    checkPreferredItems(0, "substring", "substring", "subSequence");
  }

  public void testReturnF() throws Throwable {
    checkPreferredItems(0, "false", "float", "finalize");
  }

  public void testPreferDefaultTypeToExpected() throws Throwable {
    checkPreferredItems(0, "getName", "getNameIdentifier");
  }

  public void testShorterPrefixesGoFirst() throws Throwable {
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".html");
    assertPreferredItems(0, "p", "param", "pre");
    incUseCount(lookup, 2);
    assertPreferredItems(0, "p", "pre", "param");
  }

  public void testUppercaseMatters2() throws Throwable {
    final int old = CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE;
    try {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.ALL;
      checkPreferredItems(0, "classLoader", "classLoader2");
    }
    finally {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = old;
    }
  }

  public void testShorterShouldBePreselected() throws Throwable {
    checkPreferredItems(0, "foo", "fooLongButOfDefaultType");
  }

  public void testGenericMethodsWithBoundParametersAreStillBetterThanClassLiteral() throws Throwable {
    checkPreferredItems(0, "getService", "getService", "class");
  }

  public void testClassStaticMembersInVoidContext() throws Throwable {
    checkPreferredItems(0, "booleanMethod", "voidMethod", "AN_OBJECT", "BOOLEAN", "class");
  }

  public void testJComponentInstanceMembers() throws Throwable {
    checkPreferredItems(0, "getAccessibleContext", "getUI");
  }

  public void testClassStaticMembersInBooleanContext() throws Throwable {
    final String path = getTestName(false) + ".java";
    myFixture.configureByFile(path);
    myFixture.complete(CompletionType.BASIC, 2);
    assertPreferredItems(0, "BOOLEAN", "booleanMethod", "AN_OBJECT", "voidMethod");
  }

  public void testDispreferDeclared() throws Throwable {
    checkPreferredItems(0, "aabbb", "aaa");
  }

  public void testDispreferImpls() throws Throwable {
    myFixture.addClass("package foo; public class Xxx {}");
    configureSecondCompletion();
    assertPreferredItems(0, "Xxx", "XxxEx", "XxxImpl", "Xxy");
  }

  public void testPreferOwnInnerClasses() throws Throwable {
    checkPreferredItems(0, "YyyXxx", "YyyZzz");
  }

  public void testPreferTopLevelClasses() throws Throwable {
    configureSecondCompletion();
    assertPreferredItems(0, "XxxYyy", "XxzYyy");
  }

  private void configureSecondCompletion() {
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
  }

  public void testImplsAfterNew() {
    myFixture.addClass("package foo; public interface Xxx {}");
    configureSecondCompletion();
    assertPreferredItems(0, "XxxImpl", "Xxx");
  }

  public void testPreferLessHumps() throws Throwable {
    myFixture.addClass("package foo; public interface XaYa {}");
    myFixture.addClass("package foo; public interface XyYa {}");
    configureSecondCompletion();
    assertPreferredItems(0, "XaYa", "XaYaEx", "XaYaImpl", "XyYa", "XyYaXa");
  }

  public void testPreferLessParameters() throws Throwable {
    checkPreferredItems(0, "foo", "foo", "foo", "fox");
    final List<LookupElement> items = getLookup().getItems();
    assertEquals(0, ((PsiMethod)items.get(0).getObject()).getParameterList().getParametersCount());
    assertEquals(1, ((PsiMethod)items.get(1).getObject()).getParameterList().getParametersCount());
    assertEquals(2, ((PsiMethod)items.get(2).getObject()).getParameterList().getParametersCount());
  }
  public void testStatsForClassNameInExpression() throws Throwable {
    myFixture.addClass("package foo; public interface FooBar {}");
    myFixture.addClass("package foo; public interface FooBee {}");

    invokeCompletion(getTestName(false) + ".java");
    configureSecondCompletion();
    incUseCount(getLookup(), 1);
    assertPreferredItems(0, "FooBee", "FooBar");
  }

  public void testSameStatsForDifferentQualifiers() throws Throwable {
    invokeCompletion("SameStatsForDifferentQualifiersJLabel.java");
    assertPreferredItems(0, "getComponent");
    incUseCount(getLookup(), myFixture.lookupElementStrings.indexOf('getComponents'));
    FileDocumentManager.instance.saveAllDocuments()

    invokeCompletion("SameStatsForDifferentQualifiersJLabel.java");
    assertPreferredItems(0, "getComponents", "getComponent");

    invokeCompletion("SameStatsForDifferentQualifiersJComponent.java");
    assertPreferredItems(0, "getComponents", "getComponent");
  }

  public void testSameStatsForDifferentQualifiers2() throws Throwable {
    invokeCompletion("SameStatsForDifferentQualifiersJComponent.java");
    assertPreferredItems(0, "getComponent");
    incUseCount(getLookup(), myFixture.lookupElementStrings.indexOf('getComponents'));
    FileDocumentManager.instance.saveAllDocuments()

    invokeCompletion("SameStatsForDifferentQualifiersJComponent.java");
    assertPreferredItems(0, "getComponents", "getComponent");

    invokeCompletion("SameStatsForDifferentQualifiersJLabel.java");
    assertPreferredItems(0, "getComponents", "getComponent");
  }

  public void testAbandonSameStatsForDifferentQualifiers() throws Throwable {
    invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems 0, "method1", "equals"
    myFixture.type('eq\n2);\nf2.')

    myFixture.completeBasic();
    assertPreferredItems 0, "equals", "method2"
    myFixture.type('me\n);\n')

    for (i in 0..StatisticsManager.OBLIVION_THRESHOLD) {
      myFixture.type('f2.')
      myFixture.completeBasic()
      assertPreferredItems 0, "method2", "equals"
      myFixture.type('me\n);\n')
    }

    myFixture.type('f3.')
    myFixture.completeBasic()
    assertPreferredItems 0, "method3", "equals"
  }

  public void testDispreferFinalize() throws Throwable {
    checkPreferredItems(0, "final", "finalize");
  }

  public void testPreferNewExpectedInner() throws Throwable {
    checkPreferredItems(0, "Foooo.Bar", "Foooo");

    /*final LookupElementPresentation presentation = new LookupElementPresentation();
    getLookup().getItems().get(0).renderElement(presentation);
    assertEquals("Foooo.Bar", presentation.getItemText());*/
  }

  public void testDeclaredMembersGoFirst() throws Exception {
    invokeCompletion(getTestName(false) + ".java");
    assertStringItems("fromThis", "overridden", "fromSuper", "equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait",
                      "wait", "wait");
  }

  public void testLocalVarsOverMethods() {
    checkPreferredItems(0, "value", "validate", "validateTree");
  }

  public void testCurrentClassBest() {
    checkPreferredItems(0, "XcodeProjectTemplate", "XcodeConfigurable");
  }

  public void testFqnStats() {
    myFixture.addClass("public interface Baaaaaaar {}");
    myFixture.addClass("package zoo; public interface Baaaaaaar {}");

    configureSecondCompletion();

    final LookupImpl lookup = getLookup();
    assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.getItems().get(0)).getQualifiedName());
    assertEquals("zoo.Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.getItems().get(1)).getQualifiedName());
    incUseCount(lookup, 1);

    assertEquals("zoo.Baaaaaaar", ((JavaPsiClassReferenceElement) lookup.getItems().get(0)).getQualifiedName());
    assertEquals("Baaaaaaar", ((JavaPsiClassReferenceElement)lookup.getItems().get(1)).getQualifiedName());
  }

  public void testSkipLifted() {
    checkPreferredItems(0, "hashCodeMine", "hashCode")
  }

  public void testDispreferInnerClasses() {
    checkPreferredItems(0); //no chosen items
    assertFalse(getLookup().getItems().get(0).getObject() instanceof PsiClass);
  }

  public void testPreferSameNamedMethods() {
    checkPreferredItems(0, "foo", "boo", "doo", "hashCode");
  }

  public void testPreferInterfacesInImplements() {
    checkPreferredItems(0, "XFooIntf", "XFoo", "XFooClass");
    assert LookupElementPresentation.renderElement(lookup.items[0]).itemTextForeground == JBColor.foreground()
    assert LookupElementPresentation.renderElement(lookup.items[1]).itemTextForeground == JBColor.RED
    assert LookupElementPresentation.renderElement(lookup.items[2]).itemTextForeground == JBColor.RED
  }

  public void testPreferClassesInExtends() {
    checkPreferredItems(0, "FooClass", "Foo_Intf");
  }

  public void testPreferClassStaticMembers() {
    checkPreferredItems(0, "Zoo.A", "Zoo", "Zoo.B", "Zoo.C", "Zoo.D", "Zoo.E", "Zoo.F", "Zoo.G", "Zoo.H");
  }

  public void testPreferFinallyToFinal() {
    checkPreferredItems(0, "finally", "final");
  }

  public void testPreferReturn() {
    checkPreferredItems(0, "return", "rLocal", "rParam", "rMethod");
  }

  public void testPreferReturnBeforeExpression() {
    checkPreferredItems(0, "return", "rLocal", "rParam", "rMethod");
  }

  public void testPreferModifiers() {
    checkPreferredItems(0, "private", "protected", "public");
  }

  public void testPreferEnumConstants() {
    checkPreferredItems(0, "MyEnum.bar", "MyEnum", "MyEnum.foo");
  }

  public void testPreferElse() {
    checkPreferredItems(0, "else", "element");
  }

  public void testPreferMoreMatching() {
    checkPreferredItems(0, "FooOCSomething", "FooObjectCollector");
  }

  public void testPreferSamePackageOverImported() {
    myFixture.addClass("package bar; public class Bar1 {}");
    myFixture.addClass("package bar; public class Bar2 {}");
    myFixture.addClass("package bar; public class Bar3 {}");
    myFixture.addClass("package bar; public class Bar4 {}");
    myFixture.addClass("class Bar9 {}");
    myFixture.addClass("package doo; public class Bar0 {}");

    checkPreferredItems(0, "Bar9", "Bar1", "Bar2", "Bar3", "Bar4");
  }

  public void testPreselectMostRelevantInTheMiddleAlpha() {
    UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = true;

    myFixture.addClass("package foo; public class ELXaaaaaaaaaaaaaaaaaaaa {}");
    invokeCompletion(getTestName(false) + ".java");
    myFixture.completeBasic();
    LookupImpl lookup = getLookup();
    assertPreferredItems(lookup.getList().getSelectedIndex());
    assertEquals("ELXaaaaaaaaaaaaaaaaaaaa", lookup.getItems().get(0).getLookupString());
    assertEquals("ELXEMENT_A", lookup.getCurrentItem().getLookupString());
  }

  public void testReallyAlphaSorting() {
    UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = true;

    invokeCompletion(getTestName(false) + ".java");
    assert myFixture.lookupElementStrings.sort() == myFixture.lookupElementStrings
  }

  public void testAlphaSortPackages() {
    UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = true

    def pkgs = ['bar', 'foo', 'goo', 'roo', 'zoo']
    for (s in pkgs) {
      myFixture.addClass("package $s; public class Foox {}")
    }
    invokeCompletion(getTestName(false) + ".java")
    for (i in 0..<pkgs.size()) {
      assert LookupElementPresentation.renderElement(myFixture.lookupElements[i]).tailText?.contains(pkgs[i])
    }
  }

  public void testAlphaSortingStartMatchesFirst() {
    UISettings.getInstance().SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = true
    checkPreferredItems 0, 'xxbar', 'xxfoo', 'xxgoo', 'barxx', 'fooxx', 'gooxx'
  }

  public void testSortSameNamedVariantsByProximity() {
    myFixture.addClass("public class Bar {}");
    for (int i = 0; i < 10; i++) {
      myFixture.addClass("public class Bar" + i + " {}");
      myFixture.addClass("public class Bar" + i + "Colleague {}");
    }
    myFixture.addClass("package bar; public class Bar {}");
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    assertPreferredItems(0, "Bar", "Bar");
    List<LookupElement> items = getLookup().getItems();
    assertEquals(((JavaPsiClassReferenceElement)items.get(0)).getQualifiedName(), "Bar");
    assertEquals(((JavaPsiClassReferenceElement)items.get(1)).getQualifiedName(), "bar.Bar");
  }

  public void testCaseInsensitivePrefixMatch() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    checkPreferredItems(1, "Foo", "foo1", "foo2");
  }

  public void testExpectedTypeIsMoreImportantThanCase() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    checkPreferredItems 0, "enable", "ENABLED"
    incUseCount(lookup, 1)
    assertPreferredItems 0, "ENABLED", "enable"
  }

  public void testPreferKeywordsToVoidMethodsInExpectedTypeContext() {
    checkPreferredItems 0, 'noo', 'new', 'null', 'noo2', 'notify', 'notifyAll'
  }

  public void testPreferBetterMatchingConstantToMethods() {
    checkPreferredItems 0, 'serial', 'superExpressionInIllegalContext'
  }

  public void testPreferApplicableAnnotations() throws Throwable {
    myFixture.addClass '''
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.ANNOTATION_TYPE})
@interface TMetaAnno {}

@Target({ElementType.LOCAL_VARIABLE})
@interface TLocalAnno {}'''
    checkPreferredItems 0, 'TMetaAnno', 'Target', 'TabLayoutPolicy', 'TabPlacement'
  }

  public void testPreferApplicableAnnotationsMethod() throws Throwable {
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

  public void testJComponentAddNewWithStats() throws Throwable {
    final LookupImpl lookup = invokeCompletion("/../smartTypeSorting/JComponentAddNew.java");
    assertPreferredItems(0, "FooBean3", "JComponent", "Component");
    incUseCount(lookup, 2); //Component
    assertPreferredItems(0, "Component", "FooBean3", "JComponent");
  }

  public void testDispreferReturnBeforeStatement() {
    checkPreferredItems 0, 'reaction', 'rezet', 'return'
  }

  public void testDoNotPreferGetClass() {
    checkPreferredItems 0, 'get', 'getClass'
    incUseCount(lookup, 1)
    assertPreferredItems 0, 'getClass', 'get'
    incUseCount(lookup, 1)
    assertPreferredItems 0, 'get', 'getClass'
  }

  public void testEqualsStats() {
    checkPreferredItems 0, 'equals', 'equalsIgnoreCase'
    incUseCount(lookup, 1)
    assertPreferredItems 0, 'equalsIgnoreCase', 'equals'
    incUseCount(lookup, 1)
    checkPreferredItems 0, 'equals', 'equalsIgnoreCase'
  }

  public void testPreferClassToItsConstants() {
    checkPreferredItems 0, 'Calendar.FIELD_COUNT', 'Calendar', 'Calendar.AM'
  }

  public void testPreferLocalsToStaticsInSecondCompletion() {
    myFixture.addClass('public class FooZoo { public static void fooBar() {}  }')
    myFixture.addClass('public class fooAClass {}')
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    assertPreferredItems(0, 'fooy', 'foox', 'fooAClass', 'fooBar');
  }

  public void testChangePreselectionOnSecondInvocation() {
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC);
    assertPreferredItems(0, 'fooZooGoo', 'fooZooImpl');
    myFixture.complete(CompletionType.BASIC);
    assertPreferredItems(0, 'fooZoo', 'fooZooGoo', 'fooZooImpl');
  }

  public void testUnderscoresDontMakeMatchMiddle() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    checkPreferredItems(0, 'fooBar', '_fooBar', 'FooBar')
  }

  public void testDispreferUnderscoredCaseMismatch() {
    checkPreferredItems(0, 'fooBar', '__FOO_BAR')
  }

  public void testStatisticsMattersOnNextCompletion() {
    configureByFile(getTestName(false) + ".java")
    myFixture.completeBasic();
    assert lookup
    assert lookup.currentItem.lookupString != 'JComponent'
    myFixture.type('ponent c;\nJCom')
    myFixture.completeBasic();
    assert lookup
    assert lookup.currentItem.lookupString == 'JComponent'
  }

  public void testStatisticsByPrefix() {
    Closure repeatCompletion = { String letter ->
      String var1 = "_${letter}oo1"
      String var2 = "_${letter}oo2"

      myFixture.type("_$letter");
      myFixture.completeBasic();
      assertPreferredItems(0, var1, var2)
      myFixture.type('2\n;\n')

      for (i in 0..<StatisticsManager.OBLIVION_THRESHOLD - 2) {
        myFixture.type('_');
        myFixture.completeBasic();
        assert myFixture.lookupElementStrings.indexOf(var2) < myFixture.lookupElementStrings.indexOf(var1)
        myFixture.type(letter)
        assertPreferredItems(0, var2, var1)
        myFixture.type('\n;\n')
      }
    }

    configureByFile(getTestName(false) + ".java")
    repeatCompletion 'g'
    repeatCompletion 'f'
    repeatCompletion 'b'

    myFixture.completeBasic();
    assertPreferredItems(0, 'return', '_boo2', '_foo2', '_boo1', '_foo1', '_goo1', '_goo2')
    myFixture.type('_');
    assertPreferredItems(0, '_boo2', '_foo2', '_boo1', '_foo1', '_goo1', '_goo2')
    myFixture.type('g')
    assertPreferredItems(0, '_goo2', '_goo1')
    myFixture.type('o')
    assertPreferredItems(0, '_goo2', '_goo1')
  }

  public void testPreferFieldToMethod() {
    checkPreferredItems(0, 'size', 'size')
    assert lookup.items[0].object instanceof PsiField
    assert lookup.items[1].object instanceof PsiMethod
  }

  public void testPreselectLastChosen() {
    checkPreferredItems(0, 'add', 'addAll')
    for (i in 0..10) {
      incUseCount(lookup, 1)
    }
    assertPreferredItems 0, 'addAll', 'add'
    incUseCount(lookup, 1)
    assertPreferredItems 0, 'add', 'addAll'
  }

  public void testDontPreselectLastChosenWithUnrelatedPrefix() {
    invokeCompletion(getTestName(false) + ".java")
    myFixture.type(';\nmycl')
    myFixture.completeBasic()
    assertPreferredItems 0, 'myClass', 'myExtendsClause'
  }

  public void testCommonPrefixMoreImportantThanExpectedType() {
    checkPreferredItems 0, 'myStep', 'myCurrentStep'
  }

  public void testStatsMoreImportantThanExpectedType() {
    invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems 0, 'getNumber', 'getNumProvider'
    lookup.currentItem = lookup.items[1]
    myFixture.type '\n);\ntest(getnu'
    myFixture.completeBasic()
    assertPreferredItems 0, 'getNumProvider', 'getNumber'
  }

  public void testIfConditionStats() {
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

  public void testDeepestSuperMethodStats() {
    invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems 0, 'addX', 'addY'
    myFixture.type('y\n;set1.ad')

    myFixture.completeBasic()
    assertPreferredItems 0, 'addY', 'addX'
    myFixture.type('x\n;set2.ad')

    myFixture.completeBasic()
    assertPreferredItems 0, 'addX', 'addY'
  }

  public void testCommonPrefixMoreImportantThanKind() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    checkPreferredItems(0, 'PsiElement', 'psiElement')
  }

  public void testNoExpectedTypeInStringConcatenation() {
    checkPreferredItems(0, 'vx')
  }

  public void testLocalVarsOverStats() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    checkPreferredItems 0, 'psiElement', 'PsiElement'
    incUseCount lookup, 1
    assertPreferredItems 0, 'psiElement', 'PsiElement'
  }

  public void testHonorRecency() {
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

  public void testPreferString() {
    checkPreferredItems 0, 'String', 'System', 'Set'
  }

  public void testAnnotationEnum() {
    checkPreferredItems 0, 'MyEnum.BAR', 'MyEnum', 'MyEnum.FOO'
  }

  public void testPreferClassesOfExpectedClassType() {
    myFixture.addClass "class XException extends Exception {}"
    checkPreferredItems 0, 'XException', 'XClass', 'XIntf'
  }

  public void testNoNumberValueOf() {
    checkPreferredItems 0, 'value'
  }

  public void testNoBooleansInMultiplication() {
    checkPreferredItems 0, 'fact'
  }

  public void testPreferAnnotationsToInterfaceKeyword() {
    checkPreferredItems 0, 'Deprecated', 'Override'
  }

  public void testPreferThrownExceptionsInCatch() {
    checkPreferredItems 0, 'final', 'FileNotFoundException', 'File'
  }

  public void testHonorFirstLetterCase() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    checkPreferredItems 0, 'posIdMap', 'PImageDecoder', 'PNGImageDecoder'
  }

  public void testGlobalStaticMemberStats() {
    configureNoCompletion(getTestName(false) + ".java")
    myFixture.complete(CompletionType.BASIC, 2)
    assertPreferredItems 0, 'newLinkedSet0', 'newLinkedSet1', 'newLinkedSet2'
    incUseCount lookup, 1
    assertPreferredItems 0, 'newLinkedSet1', 'newLinkedSet0', 'newLinkedSet2'
  }

  public void testStaticMemberTypes() {
    checkPreferredItems 0, 'newMap', 'newList'
  }

  public void testNoStatsInSuperInvocation() {
    checkPreferredItems 0, 'put', 'putAll'

    myFixture.type('\n')
    assert myFixture.editor.document.text.contains("put")
    
    myFixture.type(');\nsuper.')
    myFixture.completeBasic()

    assertPreferredItems 0, 'get'
  }

  public void testLiveTemplateOrdering() {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, getTestRootDisposable())
    checkPreferredItems(0, 'return')
    assert lookup.items.find { it.lookupString == 'ritar'} != null
  }

  public void testPreferLocalToExpectedTypedMethod() {
    checkPreferredItems 0, 'event', 'equals'
  }

  public void testDispreferJustUsedEnumConstantsInSwitch() {
    checkPreferredItems 0, 'BAR', 'FOO', 'GOO'
    myFixture.type('\nbreak;\ncase ')

    def items = myFixture.completeBasic()
    assertPreferredItems 0, 'FOO', 'GOO', 'BAR'

    assert LookupElementPresentation.renderElement(items[0]).itemTextForeground == JBColor.foreground()
    assert LookupElementPresentation.renderElement(items.find { it.lookupString == 'BAR' }).itemTextForeground == JBColor.RED
  }

  public void testPreferValueTypesReturnedFromMethod() {
    checkPreferredItems 0, 'StringBuffer', 'String', 'Serializable', 'SomeInterface', 'SomeInterface', 'SomeOtherClass'
    assert 'SomeInterface<String>' == LookupElementPresentation.renderElement(myFixture.lookupElements[3]).itemText
    assert 'SomeInterface' == LookupElementPresentation.renderElement(myFixture.lookupElements[4]).itemText
  }

  public void testPreferCastTypesHavingSpecifiedMethod() {
    checkPreferredItems 0, 'MainClass1', 'MainClass2', 'Maa'
  }

  public void testNaturalSorting() {
    checkPreferredItems 0, 'fun1', 'fun2', 'fun10'
  }

  public void testPreferVarsHavingReferencedMember() {
    checkPreferredItems 0, 'xzMap', 'xaString'
  }

  public void testPreferCollectionsStaticOfExpectedType() {
    checkPreferredItems 0, 'unmodifiableList', 'unmodifiableCollection'
  }

  public void testDispreferDeprecatedMethodWithUnresolvedQualifier() {
    myFixture.addClass("package foo; public class Assert { public static void assertTrue() {} }")
    myFixture.addClass("package bar; @Deprecated public class Assert { public static void assertTrue() {}; public static void assertTrue2() {} }")
    checkPreferredItems 0, 'Assert.assertTrue', 'Assert.assertTrue', 'Assert.assertTrue2'

    def p = LookupElementPresentation.renderElement(myFixture.lookup.items[0])
    assert p.tailText.contains('foo')
    assert !p.strikeout

    p = LookupElementPresentation.renderElement(myFixture.lookup.items[1])
    assert p.tailText.contains('bar')
    assert p.strikeout
  }

}
