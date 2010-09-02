/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.psi.statistics.StatisticsManager;

public class SmartTypeCompletionOrderingTest extends CompletionSortingTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/smartTypeSorting"; 

  public SmartTypeCompletionOrderingTest() {
    super(CompletionType.SMART);
  }

  public void testJComponentAdd() throws Throwable {
    checkPreferredItems(0, "name", "b", "fooBean239", "this", "getName");
  }
  
  public void testJComponentAddNew() throws Throwable {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "Container", "JComponent");
  }

  public void testJComponentAddNewWithStats() throws Throwable {
    //there's no PopupMenu in mock jdk
    final LookupImpl lookup = invokeCompletion(BASE_PATH + "/JComponentAddNew.java");
    assertPreferredItems(2, "Component", "String", "FooBean3", "Container", "JComponent");
    incUseCount(lookup, 3); //Container
    assertPreferredItems(2, "Component", "String", "Container", "FooBean3", "JComponent");
    imitateItemSelection(lookup, 3); //FooBean3
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD; i++) {
      imitateItemSelection(lookup, 2); //Container
    }
    refreshSorting(lookup);
    assertPreferredItems(0, "Container", "FooBean3", "JComponent", "Frame", "Window");
  }

  public void testMethodStats() throws Throwable {
    final LookupImpl lookup = invokeCompletion(BASE_PATH + "/" + getTestName(false) + ".java");
    assertPreferredItems(0, "bar", "foo", "goo");
    incUseCount(lookup, 2);
    assertPreferredItems(0, "goo", "bar", "foo");
  }

  public void testNewRunnable() throws Throwable {
    checkPreferredItems(0, "Runnable", "MyAnotherRunnable", "MyRunnable", "Thread");
  }

  public void testNewComponent() throws Throwable {
    checkPreferredItems(1, "Component", "Foo", "Container", "JComponent");
  }
  
  public void testClassLiteral() throws Throwable {
    checkPreferredItems(0, "String.class");
  }

  public void testMethodsWithSubstitutableReturnType() throws Throwable {
    checkPreferredItems(0, "foo", "toString", "bar");
  }

  public void testDontPreferKeywords() throws Throwable {
    checkPreferredItems(0, "o1", "foo", "name", "this", "getClass");
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
    checkPreferredItems(2, "Component", "String", "FooBean3", "JPanel", "Container");
  }
  
  public void testPreferNestedClasses() throws Throwable {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "NestedClass", "Container");
  }

  public void testSmartCollections() throws Throwable {
    checkPreferredItems(0, "s");
  }

  public void testSmartEquals() throws Throwable {
    checkPreferredItems(0, "s");
  }

  public void testSmartEquals2() throws Throwable {
    checkPreferredItems(0, "foo", "this", "o", "s", "getClass");
  }

  public void testSmartEquals3() throws Throwable {
    checkPreferredItems(0, "b", "this", "a", "z", "getClass");
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
    checkPreferredItems(0, "b", "valueOf", "valueOf", "Boolean.FALSE", "Boolean.TRUE");
  }
  
  public void testXmlTagGetAttribute() throws Throwable {
    checkPreferredItems(0, "getAttributeValue", "getNamespace", "toString");
  }

  public void testPreferFieldsToMethods() throws Throwable {
    checkPreferredItems(0, "myVersion", "getVersion", "getSelectedVersion", "calculateVersion");
  }

  public void testPreferParametersToGetters() throws Throwable {
    checkPreferredItems(0, "a", "getLastI");
  }

  public void testExpectedInterfaceShouldGoFirst() throws Throwable {
    checkPreferredItems(0, "MyProcessor<Foo>", "Proc1<Foo>");
  }

  public void testStatisticsAffectsNonPreferableExpectedItems() throws Throwable {
    final LookupImpl lookup = invokeCompletion(BASE_PATH + "/" + getTestName(false) + ".java");
    assertPreferredItems(1, "List<Foo>", "AbstractList<Foo>", "AbstractSequentialList<Foo>", "ArrayList<Foo>");
    incUseCount(lookup, 0);
    assertPreferredItems(1, "List<Foo>", "AbstractList<Foo>", "AbstractSequentialList<Foo>", "ArrayList<Foo>");
    incUseCount(lookup, 0);
    assertPreferredItems(0, "List<Foo>", "AbstractList<Foo>", "AbstractSequentialList<Foo>", "ArrayList<Foo>");
  }

  public void testPreferNonRecursiveMethodParams() throws Throwable {
    checkPreferredItems(0, "b", "s", "a", "hashCode");
  }

  public void testPreferDelegatingMethodParams() throws Throwable {
    //there's no PopupMenu in mock jdk
    final LookupImpl lookup = invokeCompletion(BASE_PATH + "/" + getTestName(false) + ".java");
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
    checkPreferredItems(0, "create", "map", "this", "getClass");
  }

  public void testLocalVarsBeforeClassLiterals() throws Throwable {
    checkPreferredItems(0, "local", "Foo.class", "Bar.class");
  }

  public void testDontPreferCasted() throws Throwable {
    checkPreferredItems(0, "b", "_o");
  }

  public void testInnerClassesProximity() throws Throwable {
    checkPreferredItems(0, "Goo", "InnerGoo", "Bar", "AGoo");
  }

  public void testLocalVariablesOutweighStats() throws Throwable {
    final LookupImpl lookup = invokeCompletion(BASE_PATH + "/" + getTestName(false) + ".java");
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
    final LookupImpl lookup = invokeCompletion(BASE_PATH + "/" + getTestName(false) + ".java");
    assertPreferredItems(0, "foo", "false");
    lookup.finishLookup(',');
    complete();
    assertPreferredItems(0, "bar", "foo", "equals", "false", "true");
  }

  public void testFieldNameOutweighsStats() throws Throwable {
    final LookupImpl lookup = invokeCompletion(BASE_PATH + "/" + getTestName(false) + ".java");
    assertPreferredItems(0, "myFoo", "myBar");
    incUseCount(lookup, 1); //myBar
    assertPreferredItems(0, "myFoo", "myBar");
  }

  protected String getBasePath() {
    return BASE_PATH;
  }
}
