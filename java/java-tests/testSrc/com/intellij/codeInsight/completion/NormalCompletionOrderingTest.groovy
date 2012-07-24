/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion;


import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.ui.UISettings
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.statistics.StatisticsManager

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
    checkPreferredItems(0, "false", "finalize");
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
    assertPreferredItems(0, "BOOLEAN", "booleanMethod", "AN_OBJECT", "voidMethod", "registerNatives");
  }

  public void testDispreferDeclared() throws Throwable {
    checkPreferredItems(0, "aabbb", "aaa");
  }

  public void testDispreferDeclaredOfExpectedType() throws Throwable {
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

  public void testDontDispreferImplsAfterNew() throws Throwable {
    myFixture.addClass("package foo; public interface Xxx {}");
    configureSecondCompletion();
    assertPreferredItems(0, "Xxx", "XxxImpl");
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
    assertPreferredItems(1, "getComponent", "getComponents");

    invokeCompletion("SameStatsForDifferentQualifiersJComponent.java");
    assertPreferredItems(1, "getComponent", "getComponents");
  }

  public void testSameStatsForDifferentQualifiers2() throws Throwable {
    invokeCompletion("SameStatsForDifferentQualifiersJComponent.java");
    assertPreferredItems(0, "getComponent");
    incUseCount(getLookup(), myFixture.lookupElementStrings.indexOf('getComponents'));
    FileDocumentManager.instance.saveAllDocuments()

    invokeCompletion("SameStatsForDifferentQualifiersJComponent.java");
    assertPreferredItems(1, "getComponent", "getComponents");

    invokeCompletion("SameStatsForDifferentQualifiersJLabel.java");
    assertPreferredItems(1, "getComponent", "getComponents");
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
    assertStringItems("fromThis", "overridden", "fromSuper", "equals", "getClass", "hashCode", "notify", "notifyAll", "toString", "wait",
                      "wait", "wait");
  }

  public void testLocalVarsOverMethods() {
    checkPreferredItems(0, "value", "validate", "validateTree", "valueOf");
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

  public void _testSkipLifted() {
    checkPreferredItems(1, "hashCode", "hashCodeMine")
  }

  public void testDispreferInnerClasses() {
    checkPreferredItems(0); //no chosen items
    assertFalse(getLookup().getItems().get(0).getObject() instanceof PsiClass);
  }

  public void testPreferSameNamedMethods() {
    checkPreferredItems(0, "foo", "boo", "doo", "hashCode");
  }

  public void testPreferInterfacesInImplements() {
    checkPreferredItems(0, "FooIntf", "FooClass");
  }

  public void testPreferClassesInExtends() {
    checkPreferredItems(0, "FooClass", "Foo_Intf");
  }

  public void testPreferClassStaticMembers() {
    checkPreferredItems(0, "Zoo", "Zoo.A", "Zoo.B", "Zoo.C", "Zoo.D", "Zoo.E", "Zoo.F", "Zoo.G", "Zoo.H");
  }

  public void testPreferFinallyToFinal() {
    checkPreferredItems(1, "final", "finally");
  }

  public void testPreferReturn() {
    checkPreferredItems(0, "return", "rLocal", "rParam", "rMethod");
  }

  public void testPreferModifiers() {
    checkPreferredItems(0, "private", "protected", "public", "paaa", "paab");
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

    myFixture.addClass("package foo; public class Elxaaaaaaaaaaaaaaaaaaaa {}");
    invokeCompletion(getTestName(false) + ".java");
    myFixture.completeBasic();
    LookupImpl lookup = getLookup();
    assertPreferredItems(lookup.getList().getSelectedIndex());
    assertEquals("Elxaaaaaaaaaaaaaaaaaaaa", lookup.getItems().get(0).getLookupString());
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
    checkPreferredItems(0, "ENABLED", "enable");
  }

  public void testPreferKeywordsToVoidMethodsInExpectedTypeContext() {
    checkPreferredItems 0, 'noo', 'new', 'null', 'noo2', 'notify', 'notifyAll'
  }

  public void testPreferBetterMatchingConstantToMethods() {
    checkPreferredItems 0, 'serial', 'superExpressionInIllegalContext'
  }

  public void testPreferApplicableAnnotations() throws Throwable {
    checkPreferredItems 0, 'ZMetaAnno', 'ZLocalAnno'
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
    assertPreferredItems 0, 'get', 'getClass'
  }

  public void testPreferClassToItsConstants() {
    checkPreferredItems 0, 'Calendar', 'Calendar.FIELD_COUNT'
  }

  public void testPreferLocalsToStaticsInSecondCompletion() {
    myFixture.addClass('public class FooZoo { public static void fooBar() {}  }')
    myFixture.addClass('public class fooAClass {}')
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC, 2);
    assertPreferredItems(0, 'fooy', 'foox', 'fooAClass', 'fooBar');
  }

  public void testChangePreselectionOnSecondInvocation() {
    myFixture.addClass('package foo; public class FooZoo { }')
    myFixture.addClass('public class FooZooImpl { }')
    myFixture.addClass('public class FooZooGoo {}')
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.BASIC);
    assertPreferredItems(0, 'FooZooGoo', 'FooZooImpl');
    myFixture.complete(CompletionType.BASIC);
    assertPreferredItems(0, 'FooZoo', 'FooZooGoo', 'FooZooImpl');
  }

  public void testUnderscoresDontMakeMatchMiddle() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE;
    checkPreferredItems(0, '_fooBar', 'FooBar')
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

}
