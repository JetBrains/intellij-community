package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.StatisticsUpdate;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;

import java.util.List;

public class SmartTypeCompletionOrderingTest extends CompletionSortingTestCase {
  public SmartTypeCompletionOrderingTest() {
    super(CompletionType.SMART);
  }

  @Override
  protected void complete() {
    myItems = myFixture.complete(CompletionType.SMART);
  }

  @NeedsIndex.ForStandardLibrary
  public void testJComponentAdd() {
    checkPreferredItems(0, "name", "b", "fooBean239", "foo");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testJComponentAddNew() {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testJComponentAddNewWithStats() {
    //there's no PopupMenu in mock jdk
    invokeCompletion("/JComponentAddNew.java");
    assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
    incUseCount(4);//Container
    assertPreferredItems(2, "Component", "String", "Container", "FooBean3", "JComponent");
    CompletionSortingTestCase.imitateItemSelection(getLookup(), 3);//FooBean3
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD; i++) {
      CompletionSortingTestCase.imitateItemSelection(getLookup(), 2);//Container
    }

    refreshSorting();
    assertPreferredItems(2, "Component", "String", "Container", "FooBean3", "JComponent");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testNewListAlwaysFirst() {
    LookupImpl lookup = invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(1, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD + 10; i++) {
      CompletionSortingTestCase.imitateItemSelection(lookup, 3);//AbstractSequentialList
    }

    refreshSorting();
    assertPreferredItems(1, "List", "AbstractSequentialList", "ArrayList", "AbstractList");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testNoStatsOnUnsuccessfulAttempt() {
    final LookupImpl lookup = invokeCompletion("/JComponentAddNew.java");
    assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
    lookup.setCurrentItem(lookup.getItems().get(4));//Container
    myFixture.type("\n\b");
    StatisticsUpdate.applyLastCompletionStatisticsUpdate();
    FileDocumentManager.getInstance().saveAllDocuments();
    invokeCompletion("/JComponentAddNew.java");
    assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
  }

  public void testMethodStats() {
    checkPreferredItems(0, "bar", "foo", "goo");
    incUseCount(2);
    assertPreferredItems(0, "goo", "bar", "foo");
  }

  @NeedsIndex.Full
  public void testNewRunnable() {
    checkPreferredItems(0, "Runnable", "MyAnotherRunnable", "MyRunnable", "Thread");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testNewComponent() {
    checkPreferredItems(1, "Component", "Foo", "JComponent", "Container");
  }

  @NeedsIndex.ForStandardLibrary
  public void testClassLiteral() throws Throwable {
    checkPreferredItems(0, "String.class");
  }

  @NeedsIndex.ForStandardLibrary
  public void testMethodsWithSubstitutableReturnType() {
    checkPreferredItems(0, "foo", "toString", "bar");
  }

  @NeedsIndex.ForStandardLibrary
  public void testDontPreferKeywords() {
    checkPreferredItems(0, "o1", "foo", "name", "this");
  }

  @NeedsIndex.ForStandardLibrary
  public void testEnumValueOf() throws Throwable {
    checkPreferredItems(0, "e", "MyEnum.BAR", "MyEnum.FOO", "valueOf", "valueOf");
  }

  public void testEnumValueOf2() throws Throwable {
    checkPreferredItems(0, "e", "MyEnum.BAR", "MyEnum.FOO", "bar", "valueOf");
  }

  public void testPreferMatchedWords() {
    checkPreferredItems(0, "getVersionString", "getTitle");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testPreferImportedClasses() {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "JPanel", "JComponent");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testPreferNestedClasses() {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "NestedClass", "JComponent");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSmartCollections() {
    checkPreferredItems(0, "s");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSmartEquals() {
    checkPreferredItems(0, "s");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSmartEquals2() {
    checkPreferredItems(0, "foo", "this", "o", "s");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSmartEquals3() {
    checkPreferredItems(0, "b", "this", "a", "z");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testSmartCollectionsNew() {
    checkPreferredItems(1, "Foo", "Bar");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testSmartEqualsNew() {
    checkPreferredItems(1, "Foo", "Bar");
  }

  @NeedsIndex.ForStandardLibrary
  public void testSmartEqualsNew2() {
    checkPreferredItems(0, "Foo");
  }

  @NeedsIndex.ForStandardLibrary
  public void testBooleanValueOf() {
    checkPreferredItems(0, "b", "Boolean.FALSE", "Boolean.TRUE", "equals", "false", "true", "valueOf", "valueOf");
  }

  @NeedsIndex.ForStandardLibrary
  public void testXmlTagGetAttribute() {
    checkPreferredItems(0, "getAttributeValue", "getNamespace", "toString");
  }

  public void testPreferFieldsToMethods() {
    checkPreferredItems(0, "myVersion", "getVersion", "getSelectedVersion", "calculateVersion");
  }

  public void testPreferFieldsToConstants() {
    checkPreferredItems(0, "dateField", "LocalDate.MAX", "LocalDate.MIN");
  }

  public void testPreferParametersToGetters() {
    checkPreferredItems(0, "a", "I._1", "getLastI", "valueOf");
  }

  public void testExpectedInterfaceShouldGoFirst() {
    checkPreferredItems(0, "MyProcessor", "Proc1");
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testStatisticsAffectsNonPreferableExpectedItems() {
    checkPreferredItems(1, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
    incUseCount(0);
    assertPreferredItems(1, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
    incUseCount(0);
    assertPreferredItems(0, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferNonRecursiveMethodParams() {
    checkPreferredItems(0, "b", "s", "a", "hashCode");
  }

  public void testPreferDelegatingMethodParams() {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(0, "xyz", "abc");
    incUseCount(1);
    assertPreferredItems(0, "xyz", "abc");
  }

  public void testGwtButtons() {
    checkPreferredItems(0, "Button", "ButtonBase");
  }

  public void testNewArrayList() {
    checkPreferredItems(0, "ArrayList", "OtherList");
  }

  public void testPassingQualifierToMethodCall() {
    checkPreferredItems(0, "this", "param");
  }

  public void testPassingThisToUnqualifiedMethodCall() {
    checkPreferredItems(0, "param", "this");
  }

  @NeedsIndex.SmartMode(reason = "isEffectivelyDeprecated needs smart mode")
  public void testPreferAccessibleMembers() {
    checkPreferredItems(0, "Foo.C_NORMAL", "Foo.B_DEPRECATED");
  }

  public void testNoSkippingInSmartCast() {
    checkPreferredItems(0, "Foo", "Bar", "Goo");
  }

  @NeedsIndex.ForStandardLibrary
  public void testLiteralInReturn() {
    checkPreferredItems(0, "false", "true", "equals");
  }

  @NeedsIndex.ForStandardLibrary
  public void testLiteralInIf() {
    checkPreferredItems(0, "equals", "false", "true");
  }

  @NeedsIndex.ForStandardLibrary
  public void testFactoryMethodForDefaultType() {
    checkPreferredItems(0, "create", "this");
  }

  @NeedsIndex.ForStandardLibrary
  public void testLocalVarsBeforeClassLiterals() {
    checkPreferredItems(0, "local", "Foo.class", "Bar.class");
  }

  public void testPreferInstanceofed() {
    checkPreferredItems(0, "_o", "b");
  }

  @NeedsIndex.Full
  public void testInnerClassesProximity() {
    checkPreferredItems(0, "Goo", "InnerGoo", "Bar", "AGoo");
  }

  public void testLocalVariablesOutweighStats() {
    checkPreferredItems(0, "foo", "param", "this", "bar", "goo");
    incUseCount(4);
    assertPreferredItems(0, "foo", "param", "this", "goo", "bar");
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD; i++) {
      CompletionSortingTestCase.imitateItemSelection(getLookup(), 3);//goo
    }

    refreshSorting();
    assertPreferredItems(0, "foo", "param", "this", "goo", "bar");
  }

  private void refreshSorting() {
    getLookup().hideLookup(true);
    complete();
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferredByNameDontChangeStatistics() {
    invokeCompletion(getTestName(false) + ".java");
    assertPreferredItems(0, "foo", "false");
    myFixture.type(",");
    complete();
    assertPreferredItems(0, "bar", "foo", "equals", "false", "true");
  }

  public void testExpectedNameDependentStats() {
    checkPreferredItems(0, "myFoo", "myBar");
    incUseCount(1);//myBar
    assertPreferredItems(0, "myBar", "myFoo");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferSameNamedMethods() {
    checkPreferredItems(0, "foo", "boo", "doo", "hashCode");
  }

  @NeedsIndex.Full
  public void testErasureNotAffectingProximity() {
    myFixture.addClass("package foo; public interface Foo<T> {}");
    myFixture.addClass("package bar; public class Bar implements foo.Foo {}");
    myFixture.addClass("public class Bar<T> implements foo.Foo<T> {}");
    checkPreferredItems(0, "Bar", "Bar");

    LookupElementPresentation presentation = new LookupElementPresentation();
    List<LookupElement> items = getLookup().getItems();

    LookupElement first = items.get(0);
    TestCase.assertEquals("Bar", ((PsiClass)first.getObject()).getQualifiedName());
    first.renderElement(presentation);
    TestCase.assertEquals("Bar<String>", presentation.getItemText());

    LookupElement second = items.get(1);
    TestCase.assertEquals("bar.Bar", ((PsiClass)second.getObject()).getQualifiedName());
    second.renderElement(presentation);
    TestCase.assertEquals("Bar", presentation.getItemText());
  }

  @NeedsIndex.Full
  public void testAssertEquals() {
    myFixture.addClass("package junit.framework; public class Assert { " +
                       "public static void assertEquals(Object a, Object b) {} " +
                       "public static void assertEquals(String a, String b) {} " +
                       "}");
    checkPreferredItems(0, "boo", "bar");
  }

  @NeedsIndex.Full
  public void testAssertEqualsJupiter() {
    myFixture.addClass("package org.junit.jupiter.api;" +
                       "public class Assertions { public static void assertEquals(Object expected, Object actual, String message) {} }");
    checkPreferredItems(0, "boo", "bar");
  }

  @NeedsIndex.Full
  public void testAssertEqualsJupiterSupplier() {
    myFixture.addClass("package java.util.function;public interface Supplier<T> {T get();}");
    myFixture.addClass("package org.junit.jupiter.api;" +
                       "public class Assertions { public static void assertEquals(Object expected, Object actual, " +
                       "java.util.function.Supplier<String> message) {} }");
    checkPreferredItems(0, "boo", "bar");
  }

  @NeedsIndex.Full
  public void testAssertNotEquals() {
    myFixture.addClass("package org.junit; public class Assert { public static void assertNotEquals(Object a, Object b) {} }");
    checkPreferredItems(0, "boo", "bar");
  }

  @NeedsIndex.Full
  public void testPreferCollectionsEmptyList() {
    myFixture.addClass("package foo; public class FList<T> implements java.util.List<T> { public static <T> FList<T> emptyList() {} }");
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.SMART, 2);
    assertTrue(ContainerUtil.indexOf(getLookup().getItems(), item -> item.getAllLookupStrings().contains("Collections.emptyList")) <
               ContainerUtil.indexOf(getLookup().getItems(), item -> item.getAllLookupStrings().contains("FList.emptyList")));
    assertPreferredItems(0, "local", "local.subList", "locMethod");
  }

  @NeedsIndex.ForStandardLibrary
  public void testDispreferGetterInSetterCall() {
    checkPreferredItems(0, "color", "getZooColor", "getColor", "hashCode");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferOtherGetterInSetterCall() {
    checkPreferredItems(0, "color", "getColor", "getZooColor", "hashCode");
  }

  public void testPreferLocalOverFactoryMatchingName() {
    checkPreferredItems(0, "e", "createEvent");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferLocalOverThis() {
    checkPreferredItems(0, "value", "this", "hashCode");
  }

  @NeedsIndex.ForStandardLibrary
  public void testGetLogger() {
    checkPreferredItems(0, "Foo.class", "forName");
  }

  @NeedsIndex.ForStandardLibrary
  public void testGetWildcardLogger() {
    checkPreferredItems(0, "Foo.class", "forName");
  }

  @NeedsIndex.ForStandardLibrary
  public void testGetWildcardFactoryLogger() {
    checkPreferredItems(0, "Foo.class", "forName");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferLocalWildcardClassOverObject() {
    checkPreferredItems(0, "type", "Object.class", "forName", "forName");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferStringsInStringConcatenation() {
    checkPreferredItems(0, "toString");
  }

  @NeedsIndex.Full
  public void testGlobalStaticMemberStats() {
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.SMART, 2);
    assertPreferredItems(0, "newLinkedSet0", "newLinkedSet1", "newLinkedSet2");
    CompletionSortingTestCase.imitateItemSelection(getLookup(), 1);
    myFixture.complete(CompletionType.SMART, 2);
    assertPreferredItems(0, "newLinkedSet1", "newLinkedSet0", "newLinkedSet2");
  }

  @NeedsIndex.Full
  public void testPreferExpectedTypeMembers() {
    configureNoCompletion(getTestName(false) + ".java");
    myFixture.complete(CompletionType.SMART, 2);
    assertPreferredItems(0, "MyColor.RED", "Another.RED");
    assert getLookup().getItems().size() == 2;
  }

  @NeedsIndex.Full
  public void testPreferGlobalMembersReturningExpectedType() {
    configureNoCompletion(getTestName(false) + ".java");
    LookupElement[] items = myFixture.complete(CompletionType.SMART, 2);
    assert NormalCompletionTestCase.renderElement(items[0]).getItemText().equals("Map.builder");
    assert NormalCompletionTestCase.renderElement(items[1]).getItemText().equals("BiMap.builder");
  }

  public void testPreferExpectedLocalOverExactlyDefaultMember() {
    checkPreferredItems(0, "method", "PsiUtil.NULL_PSI_ELEMENT");
  }

  public void testPreferStaticsOfExpectedTypeToNonStaticGetterOfGenericOne() {
    checkPreferredItems(0, "getE", "myE", "getGeneric");
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH;
  }

  private static final String BASE_PATH = "/codeInsight/completion/smartTypeSorting";
}
