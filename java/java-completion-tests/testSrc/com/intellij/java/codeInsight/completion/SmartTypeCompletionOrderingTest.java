package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.StatisticsUpdate;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.PsiClass;
import com.intellij.psi.statistics.StatisticsManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.CompletionSortingFixtureKt.completionSortingFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
@IndexingModeTemplate
public class SmartTypeCompletionOrderingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture();
  private final TestFixture<CompletionSortingFixture> completion =
    completionSortingFixture(projectFixture, CompletionType.SMART, JavaTestUtil.getJavaTestDataPath() + BASE_PATH);

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testJComponentAdd() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "name", "b", "fooBean239", "foo"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testJComponentAddNew() {
    //there's no PopupMenu in mock jdk
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testJComponentAddNewWithStats() {
    //there's no PopupMenu in mock jdk
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion("/JComponentAddNew.java");
      completion.get().assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
      completion.get().incUseCount(4);//Container
      completion.get().assertPreferredItems(2, "Component", "String", "Container", "FooBean3", "JComponent");
      CompletionSortingFixture.imitateItemSelection(completion.get().getLookup(), 3);//FooBean3
      for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD; i++) {
        CompletionSortingFixture.imitateItemSelection(completion.get().getLookup(), 2);//Container
      }

      refreshSorting();
      completion.get().assertPreferredItems(2, "Component", "String", "Container", "FooBean3", "JComponent");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testNewListAlwaysFirst() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      LookupImpl lookup = completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      completion.get().assertPreferredItems(1, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
      for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD + 10; i++) {
        CompletionSortingFixture.imitateItemSelection(lookup, 3);//AbstractSequentialList
      }

      refreshSorting();
      completion.get().assertPreferredItems(1, "List", "AbstractSequentialList", "ArrayList", "AbstractList");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testNoStatsOnUnsuccessfulAttempt() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      final LookupImpl lookup = completion.get().invokeCompletion("/JComponentAddNew.java");
      completion.get().assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
      lookup.setCurrentItem(lookup.getItems().get(4));//Container
      myFixture.type("\n\b");
      StatisticsUpdate.applyLastCompletionStatisticsUpdate();
      FileDocumentManager.getInstance().saveAllDocuments();
      completion.get().invokeCompletion("/JComponentAddNew.java");
      completion.get().assertPreferredItems(2, "Component", "String", "FooBean3", "JComponent", "Container");
    });
  }

  @Test
  public void testMethodStats() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "bar", "foo", "goo");
      completion.get().incUseCount(2);
      completion.get().assertPreferredItems(0, "goo", "bar", "foo");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testNewRunnable() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Runnable", "MyAnotherRunnable", "MyRunnable", "Thread"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testNewComponent() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(1, "Component", "Foo", "JComponent", "Container"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testClassLiteral() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "String.class"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testMethodsWithSubstitutableReturnType() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "foo", "toString", "bar"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDontPreferKeywords() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "o1", "foo", "name", "this"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testEnumValueOf() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "e", "MyEnum.BAR", "MyEnum.FOO", "valueOf", "valueOf"));
  }

  @Test
  public void testEnumValueOf2() throws Throwable {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "e", "MyEnum.BAR", "MyEnum.FOO", "bar", "valueOf"));
  }

  @Test
  public void testPreferMatchedWords() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "getVersionString", "getTitle"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testPreferImportedClasses() {
    //there's no PopupMenu in mock jdk
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(2, "Component", "String", "FooBean3", "JPanel", "JComponent"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testPreferNestedClasses() {
    //there's no PopupMenu in mock jdk
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(2, "Component", "String", "FooBean3", "NestedClass", "JComponent"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSmartCollections() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "s"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSmartEquals() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "s"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSmartEquals2() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "foo", "this", "o", "s"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSmartEquals3() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "b", "this", "a", "z"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testSmartCollectionsNew() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(1, "Foo", "Bar"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testSmartEqualsNew() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(1, "Foo", "Bar"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testSmartEqualsNew2() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Foo"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testBooleanValueOf() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "b", "Boolean.FALSE", "Boolean.TRUE", "equals", "false", "true", "valueOf", "valueOf"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testXmlTagGetAttribute() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "getAttributeValue", "getNamespace", "toString"));
  }

  @Test
  public void testPreferFieldsToMethods() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "myVersion", "getVersion", "getSelectedVersion", "calculateVersion"));
  }

  @Test
  public void testPreferFieldsToConstants() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "dateField", "LocalDate.MAX", "LocalDate.MIN"));
  }

  @Test
  public void testPreferParametersToGetters() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "a", "I._1", "getLastI", "valueOf"));
  }

  @Test
  public void testExpectedInterfaceShouldGoFirst() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "MyProcessor", "Proc1"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "AbstractExpectedTypeSkipper works in smart mode only")
  public void testStatisticsAffectsNonPreferableExpectedItems() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(1, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
      completion.get().incUseCount(0);
      completion.get().assertPreferredItems(1, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
      completion.get().incUseCount(0);
      completion.get().assertPreferredItems(0, "List", "ArrayList", "AbstractList", "AbstractSequentialList");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferNonRecursiveMethodParams() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "b", "s", "a", "hashCode"));
  }

  @Test
  public void testPreferDelegatingMethodParams() {
    //there's no PopupMenu in mock jdk
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "xyz", "abc");
      completion.get().incUseCount(1);
      completion.get().assertPreferredItems(0, "xyz", "abc");
    });
  }

  @Test
  public void testGwtButtons() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Button", "ButtonBase"));
  }

  @Test
  public void testNewArrayList() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "ArrayList", "OtherList"));
  }

  @Test
  public void testPassingQualifierToMethodCall() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "this", "param"));
  }

  @Test
  public void testPassingThisToUnqualifiedMethodCall() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "param", "this"));
  }

  @Test
  @NeedsIndex.SmartMode(reason = "isEffectivelyDeprecated needs smart mode")
  public void testPreferAccessibleMembers() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Foo.C_NORMAL", "Foo.B_DEPRECATED"));
  }

  @Test
  public void testNoSkippingInSmartCast() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Foo", "Bar", "Goo"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLiteralInReturn() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "false", "true", "equals"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLiteralInIf() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "equals", "false", "true"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testFactoryMethodForDefaultType() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "create", "this"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLocalVarsBeforeClassLiterals() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "local", "Foo.class", "Bar.class"));
  }

  @Test
  public void testPreferInstanceofed() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "_o", "b"));
  }

  @Test
  @NeedsIndex.Full
  public void testInnerClassesProximity() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Goo", "InnerGoo", "Bar", "AGoo"));
  }

  @Test
  public void testLocalVariablesOutweighStats() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "foo", "param", "this", "bar", "goo");
      completion.get().incUseCount(4);
      completion.get().assertPreferredItems(0, "foo", "param", "this", "goo", "bar");
      for (int i = 0; i < StatisticsManager.OBLIVION_THRESHOLD; i++) {
        CompletionSortingFixture.imitateItemSelection(completion.get().getLookup(), 3);//goo
      }

      refreshSorting();
      completion.get().assertPreferredItems(0, "foo", "param", "this", "goo", "bar");
    });
  }

  private void refreshSorting() {
    completion.get().getLookup().hideLookup(true);
    completion.get().complete();
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferredByNameDontChangeStatistics() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().invokeCompletion(myFixture.getTestName(false) + ".java");
      completion.get().assertPreferredItems(0, "foo", "false");
      myFixture.type(",");
      completion.get().complete();
      completion.get().assertPreferredItems(0, "bar", "foo", "equals", "false", "true");
    });
  }

  @Test
  public void testExpectedNameDependentStats() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().checkPreferredItems(0, "myFoo", "myBar");
      completion.get().incUseCount(1);//myBar
      completion.get().assertPreferredItems(0, "myBar", "myFoo");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferSameNamedMethods() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "foo", "boo", "doo", "hashCode"));
  }

  @Test
  @NeedsIndex.Full
  public void testErasureNotAffectingProximity() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public interface Foo<T> {}");
      myFixture.addClass("package bar; public class Bar implements foo.Foo {}");
      myFixture.addClass("public class Bar<T> implements foo.Foo<T> {}");
      completion.get().checkPreferredItems(0, "Bar", "Bar");

      LookupElementPresentation presentation = new LookupElementPresentation();
      List<LookupElement> items = completion.get().getLookup().getItems();

      LookupElement first = items.get(0);
      assertEquals("Bar", ((PsiClass)first.getObject()).getQualifiedName());
      first.renderElement(presentation);
      assertEquals("Bar<String>", presentation.getItemText());

      LookupElement second = items.get(1);
      assertEquals("bar.Bar", ((PsiClass)second.getObject()).getQualifiedName());
      second.renderElement(presentation);
      assertEquals("Bar", presentation.getItemText());
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAssertEquals() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package junit.framework; public class Assert { " +
                         "public static void assertEquals(Object a, Object b) {} " +
                         "public static void assertEquals(String a, String b) {} " +
                         "}");
      completion.get().checkPreferredItems(0, "boo", "bar");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAssertEqualsJupiter() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package org.junit.jupiter.api;" +
                         "public class Assertions { public static void assertEquals(Object expected, Object actual, String message) {} }");
      completion.get().checkPreferredItems(0, "boo", "bar");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAssertEqualsJupiterSupplier() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package java.util.function;public interface Supplier<T> {T get();}");
      myFixture.addClass("package org.junit.jupiter.api;" +
                         "public class Assertions { public static void assertEquals(Object expected, Object actual, " +
                         "java.util.function.Supplier<String> message) {} }");
      completion.get().checkPreferredItems(0, "boo", "bar");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testAssertNotEquals() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package org.junit; public class Assert { public static void assertNotEquals(Object a, Object b) {} }");
      completion.get().checkPreferredItems(0, "boo", "bar");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPreferCollectionsEmptyList() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package foo; public class FList<T> implements java.util.List<T> { public static <T> FList<T> emptyList() {} }");
      completion.get().configureNoCompletion(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.SMART, 2);
      assertTrue(ContainerUtil.indexOf(completion.get().getLookup().getItems(), item -> item.getAllLookupStrings().contains("Collections.emptyList")) <
                 ContainerUtil.indexOf(completion.get().getLookup().getItems(), item -> item.getAllLookupStrings().contains("FList.emptyList")));
      completion.get().assertPreferredItems(0, "local", "local.subList", "locMethod");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDispreferGetterInSetterCall() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "color", "getZooColor", "getColor", "hashCode"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferOtherGetterInSetterCall() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "color", "getColor", "getZooColor", "hashCode"));
  }

  @Test
  public void testPreferLocalOverFactoryMatchingName() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "e", "createEvent"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferLocalOverThis() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "value", "this", "hashCode"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGetLogger() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Foo.class", "forName"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGetWildcardLogger() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Foo.class", "forName"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGetWildcardFactoryLogger() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "Foo.class", "forName"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferLocalWildcardClassOverObject() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "type", "Object.class", "forName", "forName"));
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferStringsInStringConcatenation() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "toString"));
  }

  @Test
  @NeedsIndex.Full
  public void testGlobalStaticMemberStats() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureNoCompletion(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.SMART, 2);
      completion.get().assertPreferredItems(0, "newLinkedSet0", "newLinkedSet1", "newLinkedSet2");
      CompletionSortingFixture.imitateItemSelection(completion.get().getLookup(), 1);
      myFixture.complete(CompletionType.SMART, 2);
      completion.get().assertPreferredItems(0, "newLinkedSet1", "newLinkedSet0", "newLinkedSet2");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPreferExpectedTypeMembers() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureNoCompletion(myFixture.getTestName(false) + ".java");
      myFixture.complete(CompletionType.SMART, 2);
      completion.get().assertPreferredItems(0, "MyColor.RED", "Another.RED");
      assert completion.get().getLookup().getItems().size() == 2;
    });
  }

  @Test
  @NeedsIndex.Full
  public void testPreferGlobalMembersReturningExpectedType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureNoCompletion(myFixture.getTestName(false) + ".java");
      LookupElement[] items = myFixture.complete(CompletionType.SMART, 2);
      assert NormalCompletionFixture.renderElement(items[0]).getItemText().equals("Map.builder");
      assert NormalCompletionFixture.renderElement(items[1]).getItemText().equals("BiMap.builder");
    });
  }

  @Test
  public void testPreferExpectedLocalOverExactlyDefaultMember() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "method", "PsiUtil.NULL_PSI_ELEMENT"));
  }

  @Test
  public void testPreferStaticsOfExpectedTypeToNonStaticGetterOfGenericOne() {
    EdtTestUtil.runInEdtAndWait(() -> completion.get().checkPreferredItems(0, "getE", "myE", "getGeneric"));
  }

  private static final String BASE_PATH = "/codeInsight/completion/smartTypeSorting";
}
