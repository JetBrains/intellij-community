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
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.StatisticsUpdate
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiClass
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.testFramework.NeedsIndex

class SmartTypeCompletionOrderingTest extends CompletionSortingTestCase {
  private static final String BASE_PATH = "/codeInsight/completion/smartTypeSorting"

  SmartTypeCompletionOrderingTest() {
    super(CompletionType.SMART)
  }

  @NeedsIndex.ForStandardLibrary
  void testJComponentAdd() throws Throwable {
    checkPreferredItems(0, "name", "b", "fooBean239", "foo")
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testJComponentAddNew() throws Throwable {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container")
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testJComponentAddNewWithStats() throws Throwable {
    //there's no PopupMenu in mock jdk
    final LookupImpl lookup = invokeCompletion("/JComponentAddNew.java")
    assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container")
    incUseCount(lookup, 4) //Container
    assertPreferredItems(2, "Component", "String", "Container", "FooBean3", "JComponent")
    imitateItemSelection(lookup, 3) //FooBean3
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD; i++) {
      imitateItemSelection(lookup, 2) //Container
    }
    refreshSorting(lookup)
    assertPreferredItems(2, "Component", "String", "Container", "FooBean3", "JComponent")
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testNewListAlwaysFirst() {
    def lookup = invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems 1, 'List', 'ArrayList', 'AbstractList', 'AbstractSequentialList'
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD + 10; i++) {
      imitateItemSelection(lookup, 3) //AbstractSequentialList
    }
    refreshSorting(lookup)
    assertPreferredItems 1, 'List', 'AbstractSequentialList', 'ArrayList', 'AbstractList'
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testNoStatsOnUnsuccessfulAttempt() {
    final LookupImpl lookup = invokeCompletion("/JComponentAddNew.java")
    assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container")
    lookup.currentItem = lookup.items[4] //Container
    myFixture.type('\n\b')
    StatisticsUpdate.applyLastCompletionStatisticsUpdate()
    FileDocumentManager.instance.saveAllDocuments()
    invokeCompletion("/JComponentAddNew.java")
    assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container")
  }

  void testMethodStats() throws Throwable {
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems(0, "bar", "foo", "goo")
    incUseCount(lookup, 2)
    assertPreferredItems(0, "goo", "bar", "foo")
  }

  @NeedsIndex.Full
  void testNewRunnable() throws Throwable {
    checkPreferredItems(0, "Runnable", "MyAnotherRunnable", "MyRunnable", "Thread")
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testNewComponent() throws Throwable {
    checkPreferredItems(1, "Component", "Foo", "JComponent", "Container")
  }

  @NeedsIndex.ForStandardLibrary
  void testClassLiteral() throws Throwable {
    checkPreferredItems(0, "String.class")
  }

  @NeedsIndex.ForStandardLibrary
  void testMethodsWithSubstitutableReturnType() throws Throwable {
    checkPreferredItems(0, "foo", "toString", "bar")
  }

  @NeedsIndex.ForStandardLibrary
  void testDontPreferKeywords() throws Throwable {
    checkPreferredItems(0, "o1", "foo", "name", "this")
  }

  @NeedsIndex.ForStandardLibrary
  void testEnumValueOf() throws Throwable {
    checkPreferredItems(0, "e", "MyEnum.BAR", "MyEnum.FOO", "valueOf", "valueOf")
  }

  void testEnumValueOf2() throws Throwable {
    checkPreferredItems(0, "e", "MyEnum.BAR", "MyEnum.FOO", "bar", "valueOf")
  }

  void testPreferMatchedWords() throws Throwable {
    checkPreferredItems(0, "getVersionString", "getTitle")
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testPreferImportedClasses() throws Throwable {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "JPanel", "JComponent")
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testPreferNestedClasses() throws Throwable {
    //there's no PopupMenu in mock jdk
    checkPreferredItems(2, "Component", "String", "FooBean3", "NestedClass", "JComponent")
  }

  @NeedsIndex.ForStandardLibrary
  void testSmartCollections() throws Throwable {
    checkPreferredItems(0, "s")
  }

  @NeedsIndex.ForStandardLibrary
  void testSmartEquals() throws Throwable {
    checkPreferredItems(0, "s")
  }

  @NeedsIndex.ForStandardLibrary
  void testSmartEquals2() throws Throwable {
    checkPreferredItems(0, "foo", "this", "o", "s")
  }

  @NeedsIndex.ForStandardLibrary
  void testSmartEquals3() throws Throwable {
    checkPreferredItems(0, "b", "this", "a", "z")
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testSmartCollectionsNew() throws Throwable {
    checkPreferredItems(1, "Foo", "Bar")
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testSmartEqualsNew() throws Throwable {
    checkPreferredItems(1, "Foo", "Bar")
  }

  @NeedsIndex.ForStandardLibrary
  void testSmartEqualsNew2() throws Throwable {
    checkPreferredItems(0, "Foo")
  }

  @NeedsIndex.ForStandardLibrary
  void testBooleanValueOf() throws Throwable {
    checkPreferredItems(0, "b", "Boolean.FALSE", "Boolean.TRUE", "equals", "false", "true", "valueOf", "valueOf")
  }

  @NeedsIndex.ForStandardLibrary
  void testXmlTagGetAttribute() throws Throwable {
    checkPreferredItems(0, "getAttributeValue", "getNamespace", "toString")
  }

  void testPreferFieldsToMethods() throws Throwable {
    checkPreferredItems(0, "myVersion", "getVersion", "getSelectedVersion", "calculateVersion")
  }

  void testPreferFieldsToConstants() {
    checkPreferredItems(0, "dateField", "LocalDate.MAX", "LocalDate.MIN")
  }

  void testPreferParametersToGetters() throws Throwable {
    checkPreferredItems(0, "a", "I._1", "getLastI", "valueOf")
  }

  void testExpectedInterfaceShouldGoFirst() throws Throwable {
    checkPreferredItems(0, "MyProcessor", "Proc1")
  }

  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  void testStatisticsAffectsNonPreferableExpectedItems() throws Throwable {
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems(1, "List", "ArrayList", "AbstractList", "AbstractSequentialList")
    incUseCount(lookup, 0)
    assertPreferredItems(1, "List", "ArrayList", "AbstractList", "AbstractSequentialList")
    incUseCount(lookup, 0)
    assertPreferredItems(0, "List", "ArrayList", "AbstractList", "AbstractSequentialList")
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferNonRecursiveMethodParams() throws Throwable {
    checkPreferredItems(0, "b", "s", "a", "hashCode")
  }

  void testPreferDelegatingMethodParams() throws Throwable {
    //there's no PopupMenu in mock jdk
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems(0, "xyz", "abc")
    incUseCount(lookup, 1)
    assertPreferredItems(0, "xyz", "abc")
  }

  void testGwtButtons() throws Throwable {
    checkPreferredItems(0, "Button", "ButtonBase")
  }

  void testNewArrayList() throws Throwable {
    checkPreferredItems(0, "ArrayList", "OtherList")
  }

  void testPassingQualifierToMethodCall() throws Throwable {
    checkPreferredItems(0, "this", "param")
  }

  void testPassingThisToUnqualifiedMethodCall() throws Throwable {
    checkPreferredItems(0, "param", "this")
  }

  void testPreferAccessibleMembers() throws Throwable {
    checkPreferredItems(0, "Foo.C_NORMAL", "Foo.B_DEPRECATED")
  }

  void testNoSkippingInSmartCast() throws Throwable {
    checkPreferredItems(0, "Foo", "Bar", "Goo")
  }

  @NeedsIndex.ForStandardLibrary
  void testLiteralInReturn() throws Throwable {
    checkPreferredItems(0, "false", "true", "equals")
  }

  @NeedsIndex.ForStandardLibrary
  void testLiteralInIf() throws Throwable {
    checkPreferredItems(0, "equals", "false", "true")
  }

  @NeedsIndex.ForStandardLibrary
  void testFactoryMethodForDefaultType() throws Throwable {
    checkPreferredItems(0, "create", "this")
  }

  @NeedsIndex.ForStandardLibrary
  void testLocalVarsBeforeClassLiterals() throws Throwable {
    checkPreferredItems(0, "local", "Foo.class", "Bar.class")
  }

  void testPreferInstanceofed() throws Throwable {
    checkPreferredItems(0, "_o", "b")
  }

  @NeedsIndex.Full
  void testInnerClassesProximity() throws Throwable {
    checkPreferredItems(0, "Goo", "InnerGoo", "Bar", "AGoo")
  }

  void testLocalVariablesOutweighStats() throws Throwable {
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems(0, "foo", "param", "this", "bar", "goo")
    incUseCount(lookup, 4)
    assertPreferredItems(0, "foo", "param", "this", "goo", "bar")
    for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD; i++) {
      imitateItemSelection(lookup, 3) //goo
    }
    refreshSorting(lookup)
    assertPreferredItems(0, "foo", "param", "this", "goo", "bar")
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferredByNameDontChangeStatistics() throws Throwable {
    invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems(0, "foo", "false")
    myFixture.type(',')
    myFixture.complete(CompletionType.SMART)
    assertPreferredItems(0, "bar", "foo", "equals", "false", "true")
  }

  void testExpectedNameDependentStats() throws Throwable {
    final LookupImpl lookup = invokeCompletion(getTestName(false) + ".java")
    assertPreferredItems(0, "myFoo", "myBar")
    incUseCount(lookup, 1) //myBar
    assertPreferredItems(0, "myBar", "myFoo")
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferSameNamedMethods() {
    checkPreferredItems(0, "foo", "boo", "doo", "hashCode")
  }

  @NeedsIndex.Full
  void testErasureNotAffectingProximity() {
    myFixture.addClass("package foo; public interface Foo<T> {}")
    myFixture.addClass("package bar; public class Bar implements foo.Foo {}")
    myFixture.addClass("public class Bar<T> implements foo.Foo<T> {}")
    checkPreferredItems(0, "Bar", "Bar")

    LookupElementPresentation presentation = new LookupElementPresentation()
    List<LookupElement> items = getLookup().getItems()

    LookupElement first = items.get(0)
    assertEquals("Bar", ((PsiClass)first.getObject()).getQualifiedName())
    first.renderElement(presentation)
    assertEquals("Bar<String>", presentation.getItemText())

    LookupElement second = items.get(1)
    assertEquals("bar.Bar", ((PsiClass)second.getObject()).getQualifiedName())
    second.renderElement(presentation)
    assertEquals("Bar", presentation.getItemText())
  }

  @NeedsIndex.Full
  void testAssertEquals() throws Throwable {
    myFixture.addClass("package junit.framework; public class Assert { public static void assertEquals(Object a, Object b) {} }")
    checkPreferredItems(0, "boo", "bar")
  }

  @NeedsIndex.Full
  void testPreferCollectionsEmptyList() throws Throwable {
    myFixture.addClass("package foo; public class FList<T> implements java.util.List<T> { public static <T> FList<T> emptyList() {} }")
    configureNoCompletion(getTestName(false) + ".java")
    myFixture.complete(CompletionType.SMART, 2)
    assert lookup.items.findIndexOf { 'Collections.emptyList' in it.allLookupStrings } < lookup.items.findIndexOf { 'FList.emptyList' in it.allLookupStrings }
    assertPreferredItems(0, "local", "local.subList", "locMethod")
  }

  @NeedsIndex.ForStandardLibrary
  void testDispreferGetterInSetterCall() {
    checkPreferredItems 0, 'color', 'getZooColor', 'getColor', 'hashCode'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferOtherGetterInSetterCall() {
    checkPreferredItems 0, 'color', 'getColor', 'getZooColor', 'hashCode'
  }

  void testPreferLocalOverFactoryMatchingName() {
    checkPreferredItems 0, 'e', 'createEvent'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferLocalOverThis() {
    checkPreferredItems 0, 'value', 'this', 'hashCode'
  }

  @NeedsIndex.ForStandardLibrary
  void testGetLogger() {
    checkPreferredItems 0, 'Foo.class', 'forName'
  }

  @NeedsIndex.ForStandardLibrary
  void testGetWildcardLogger() {
    checkPreferredItems 0, 'Foo.class', 'forName'
  }

  @NeedsIndex.ForStandardLibrary
  void testGetWildcardFactoryLogger() {
    checkPreferredItems 0, 'Foo.class', 'forName'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferLocalWildcardClassOverObject() {
    checkPreferredItems 0, 'type', 'Object.class', 'forName', 'forName'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferStringsInStringConcatenation() {
    checkPreferredItems 0, 'toString'
  }

  @NeedsIndex.Full
  void testGlobalStaticMemberStats() {
    configureNoCompletion(getTestName(false) + ".java")
    myFixture.complete(CompletionType.SMART, 2)
    assertPreferredItems 0, 'newLinkedSet0', 'newLinkedSet1', 'newLinkedSet2'
    incUseCount lookup, 1
    assertPreferredItems 0, 'newLinkedSet1', 'newLinkedSet0', 'newLinkedSet2'
  }

  @NeedsIndex.Full
  void testPreferExpectedTypeMembers() {
    configureNoCompletion(getTestName(false) + ".java")
    myFixture.complete(CompletionType.SMART, 2)
    assertPreferredItems 0, 'MyColor.RED', 'Another.RED'
    assert lookup.items.size() == 2
  }

  @NeedsIndex.Full
  void testPreferGlobalMembersReturningExpectedType() {
    configureNoCompletion(getTestName(false) + ".java")
    def items = myFixture.complete(CompletionType.SMART, 2)
    assert LookupElementPresentation.renderElement(items[0]).itemText == 'Map.builder'
    assert LookupElementPresentation.renderElement(items[1]).itemText == 'BiMap.builder'
  }

  void testPreferExpectedLocalOverExactlyDefaultMember() {
    checkPreferredItems 0, 'method', 'PsiUtil.NULL_PSI_ELEMENT'
  }

  void testPreferStaticsOfExpectedTypeToNonStaticGetterOfGenericOne() {
    checkPreferredItems 0, 'getE', 'myE', 'getGeneric'
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + BASE_PATH
  }
}
