/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion;


import com.intellij.JavaTestUtil
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.psi.PsiClass
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.openapi.fileEditor.FileDocumentManager

public class SmartTypeCompletionOrderingTest extends CompletionSortingTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/smartTypeSorting"; 

  public SmartTypeCompletionOrderingTest() {
    super(CompletionType.SMART);
  }

  public void testJComponentAdd() throws Throwable {
    checkPreferredItems(0, "name", "b", "fooBean239", "foo", "this");
  }
  
  public void testJComponentAddNew() throws Throwable {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
  }

  public void testJComponentAddNewWithStats() throws Throwable {
    //there's no PopupMenu in mock jdk
    final LookupImpl lookup = invokeCompletion("/JComponentAddNew.java");
    assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
    incUseCount(lookup, 4); //Container
    assertPreferredItems(2, "Component", "String", "Container", "FooBean3", "JComponent");
    imitateItemSelection(lookup, 3); //FooBean3
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD; i++) {
      imitateItemSelection(lookup, 2); //Container
    }
    refreshSorting(lookup);
    assertPreferredItems(2, "Component", "String", "Container", "FooBean3", "JComponent");

    int component = lookup.items.findIndexOf { it.lookupString == 'Component' }
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD; i++) {
      imitateItemSelection(lookup, component);
    }
    refreshSorting(lookup);
    assertPreferredItems(1, "String", "Component", "FooBean3");
  }

  public void testNewListAlwaysFirst() {
    def lookup = invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems 1, 'List', 'ArrayList', 'AbstractList', 'AbstractSequentialList'
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD + 10; i++) {
      imitateItemSelection(lookup, 3) //AbstractSequentialList
    }
    refreshSorting(lookup)
    assertPreferredItems 1, 'List', 'AbstractSequentialList', 'ArrayList', 'AbstractList'
  }
  
  public void testNoStatsOnUnsuccessfulAttempt() {
    final LookupImpl lookup = invokeCompletion("/JComponentAddNew.java");
    assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
    lookup.currentItem = lookup.items[4] //Container
    myFixture.type('\n\b')
    CompletionLookupArranger.applyLastCompletionStatisticsUpdate()
    FileDocumentManager.instance.saveAllDocuments()
    invokeCompletion("/JComponentAddNew.java")
    assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
  }

  public void testMethodStats() throws Throwable {
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(0, "bar", "foo", "goo");
    incUseCount(lookup, 2);
    assertPreferredItems(0, "goo", "bar", "foo");
  }

  public void testNewRunnable() throws Throwable {
    checkPreferredItems(0, "Runnable", "MyAnotherRunnable", "MyRunnable", "Thread");
  }

  public void testNewComponent() throws Throwable {
    checkPreferredItems(1, "Component", "Foo", "JComponent", "Container");
  }
  
  public void testClassLiteral() throws Throwable {
    checkPreferredItems(0, "String.class");
  }

  public void testMethodsWithSubstitutableReturnType() throws Throwable {
    checkPreferredItems(0, "foo", "toString", "bar");
  }

  public void testDontPreferKeywords() throws Throwable {
    checkPreferredItems(0, "o1", "foo", "name", "this");
  }

  public void testEnumValueOf() throws Throwable {
    checkPreferredItems(0, "e", "MyEnum.BAR", "MyEnum.FOO", "valueOf", "valueOf");
  }

  public void testEnumValueOf2() throws Throwable {
    checkPreferredItems(0, "e", "MyEnum.BAR", "MyEnum.FOO", "bar", "valueOf");
  }

  public void testPreferMatchedWords() throws Throwable {
    checkPreferredItems(0, "getVersionString", "getTitle");
  }

  public void testPreferImportedClasses() throws Throwable {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "JPanel", "JComponent");
  }
  
  public void testPreferNestedClasses() throws Throwable {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "NestedClass", "JComponent");
  }

  public void testSmartCollections() throws Throwable {
    checkPreferredItems(0, "s");
  }

  public void testSmartEquals() throws Throwable {
    checkPreferredItems(0, "s");
  }

  public void testSmartEquals2() throws Throwable {
    checkPreferredItems(0, "foo", "this", "o", "s");
  }

  public void testSmartEquals3() throws Throwable {
    checkPreferredItems(0, "b", "this", "a", "z");
  }

  public void testSmartCollectionsNew() throws Throwable {
    checkPreferredItems(1, "Foo", "Bar");
  }

  public void testSmartEqualsNew() throws Throwable {
    checkPreferredItems(1, "Foo", "Bar");
  }
  
  public void testSmartEqualsNew2() throws Throwable {
    checkPreferredItems(0, "Foo");
  }

  public void testBooleanValueOf() throws Throwable {
    checkPreferredItems(0, "b", "Boolean.FALSE", "Boolean.TRUE", "equals", "false", "true", "valueOf", "valueOf");
  }
  
  public void testXmlTagGetAttribute() throws Throwable {
    checkPreferredItems(0, "getAttributeValue", "getNamespace", "toString");
  }

  public void testPreferFieldsToMethods() throws Throwable {
    checkPreferredItems(0, "myVersion", "getVersion", "getSelectedVersion", "calculateVersion");
  }

  public void testPreferFieldsToConstants() {
    checkPreferredItems(0, "dateField", "LocalDate.MAX", "LocalDate.MIN");
  }

  public void testPreferParametersToGetters() throws Throwable {
    checkPreferredItems(0, "a", "I._1", "getLastI", "valueOf");
  }

  public void testExpectedInterfaceShouldGoFirst() throws Throwable {
    checkPreferredItems(0, "MyProcessor", "Proc1");
  }

  public void testStatisticsAffectsNonPreferableExpectedItems() throws Throwable {
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(1, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
    incUseCount(lookup, 0);
    assertPreferredItems(1, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
    incUseCount(lookup, 0);
    assertPreferredItems(0, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
  }

  public void testPreferNonRecursiveMethodParams() throws Throwable {
    checkPreferredItems(0, "b", "s", "a", "hashCode");
  }

  public void testPreferDelegatingMethodParams() throws Throwable {
    //there's no PopupMenu in mock jdk
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(0, "xyz", "abc");
    incUseCount(lookup, 1);
    assertPreferredItems(0, "xyz", "abc");
  }

  public void testGwtButtons() throws Throwable {
    checkPreferredItems(0, "Button", "ButtonBase");
  }

  public void testNewArrayList() throws Throwable {
    checkPreferredItems(0, "ArrayList", "OtherList");
  }

  public void testPassingQualifierToMethodCall() throws Throwable {
    checkPreferredItems(0, "this", "param");
  }

  public void testPassingThisToUnqualifiedMethodCall() throws Throwable {
    checkPreferredItems(0, "param", "this");
  }

  public void testPreferAccessibleMembers() throws Throwable {
    checkPreferredItems(0, "Foo.C_NORMAL", "Foo.B_DEPRECATED");
  }

  public void testNoSkippingInSmartCast() throws Throwable {
    checkPreferredItems(0, "Foo", "Bar", "Goo");
  }

  public void testLiteralInReturn() throws Throwable {
    checkPreferredItems(0, "false", "true", "equals");
  }

  public void testLiteralInIf() throws Throwable {
    checkPreferredItems(0, "equals", "false", "true");
  }

  public void testFactoryMethodForDefaultType() throws Throwable {
    checkPreferredItems(0, "create", "this");
  }

  public void testLocalVarsBeforeClassLiterals() throws Throwable {
    checkPreferredItems(0, "local", "Foo.class", "Bar.class");
  }

  public void testPreferInstanceofed() throws Throwable {
    checkPreferredItems(0, "_o", "b");
  }

  public void testInnerClassesProximity() throws Throwable {
    checkPreferredItems(0, "Goo", "InnerGoo", "Bar", "AGoo");
  }

  public void testLocalVariablesOutweighStats() throws Throwable {
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(0, "foo", "param", "this", "bar", "goo");
    incUseCount(lookup, 4);
    assertPreferredItems(0, "foo", "param", "this", "goo", "bar");
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD; i++) {
      imitateItemSelection(lookup, 3); //goo
    }
    refreshSorting(lookup);
    assertPreferredItems(0, "foo", "param", "this", "goo", "bar");
  }

  public void testPreferredByNameDontChangeStatistics() throws Throwable {
    invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(0, "foo", "false");
    myFixture.type(',');
    complete();
    assertPreferredItems(0, "bar", "foo", "equals", "false", "true");
  }

  public void testExpectedNameDependentStats() throws Throwable {
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(0, "myFoo", "myBar");
    incUseCount(lookup, 1); //myBar
    assertPreferredItems(0, "myBar", "myFoo");
  }

  public void testPreferSameNamedMethods() {
    checkPreferredItems(0, "foo", "boo", "doo", "hashCode");
  }

  public void testErasureNotAffectingProximity() {
    myFixture.addClass("package foo; public interface Foo<T> {}");
    myFixture.addClass("package bar; public class Bar implements foo.Foo {}");
    myFixture.addClass("public class Bar<T> implements foo.Foo<T> {}");
    checkPreferredItems(0, "Bar", "Bar");

    LookupElementPresentation presentation = new LookupElementPresentation();
    List<LookupElement> items = getLookup().getItems();

    LookupElement first = items.get(0);
    assertEquals("Bar", ((PsiClass)first.getObject()).getQualifiedName());
    first.renderElement(presentation);
    assertEquals("Bar<String>", presentation.getItemText());

    LookupElement second = items.get(1);
    assertEquals("bar.Bar", ((PsiClass)second.getObject()).getQualifiedName());
    second.renderElement(presentation);
    assertEquals("Bar", presentation.getItemText());
  }

  public void testAssertEquals() throws Throwable {
    myFixture.addClass("package junit.framework; public class Assert { public static void assertEquals(Object a, Object b) {} }");
    checkPreferredItems(0, "boo", "bar")
  }

  public void testPreferCollectionsEmptyList() throws Throwable {
    myFixture.addClass("package foo; public class FList<T> implements java.util.List<T> { public static <T> FList<T> emptyList() {} }");
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.SMART, 2);
    assert lookup.items.findIndexOf { 'Collections.emptyList' in it.allLookupStrings } < lookup.items.findIndexOf { 'FList.emptyList' in it.allLookupStrings }
    assertPreferredItems(0, "local", "local.subList", "locMethod")
  }

  public void testDispreferGetterInSetterCall() {
    checkPreferredItems 0, 'color', 'getZooColor', 'getColor', 'hashCode'
  }
  public void testPreferOtherGetterInSetterCall() {
    checkPreferredItems 0, 'color', 'getColor', 'getZooColor', 'hashCode'
  }
  public void testPreferLocalOverFactoryMatchingName() {
    checkPreferredItems 0, 'e', 'createEvent'
  }
  public void testPreferLocalOverThis() {
    checkPreferredItems 0, 'value', 'this', 'hashCode'
  }

  public void testGetLogger() {
    checkPreferredItems 0, 'Foo.class', 'forName'
  }
  public void testGetWildcardLogger() {
    checkPreferredItems 0, 'Foo.class', 'forName'
  }
  public void testPreferLocalWildcardClassOverObject() {
    checkPreferredItems 0, 'type', 'Object.class'
  }

  public void testPreferStringsInStringConcatenation() {
    checkPreferredItems 0, 'toString'
  }

  public void testGlobalStaticMemberStats() {
    configureNoCompletion(getTestName(false) + ".java")
    myFixture.complete(CompletionType.SMART, 2)
    assertPreferredItems 0, 'newLinkedSet0', 'newLinkedSet1', 'newLinkedSet2'
    incUseCount lookup, 1
    assertPreferredItems 0, 'newLinkedSet1', 'newLinkedSet0', 'newLinkedSet2'
  }

  public void testPreferExpectedTypeMembers() {
    configureNoCompletion(getTestName(false) + ".java")
    myFixture.complete(CompletionType.SMART, 2)
    assertPreferredItems 0, 'MyColor.RED', 'Another.RED'
    assert lookup.items.size() == 2
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH;
  }
}
