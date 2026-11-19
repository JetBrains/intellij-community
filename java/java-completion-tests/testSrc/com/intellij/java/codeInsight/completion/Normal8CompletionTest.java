package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author anna
 */
@TestApplication
@IndexingModeTemplate
public class Normal8CompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<NormalCompletionFixture> completion =
    normalCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/lambda/completion/normal/");

  @Test
  public void testSelfStaticsOnly() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByFile("SelfStaticsOnly.java");
      completion.get().assertStringItems("ba", "bar");
    });
  }

  @Test
  public void testFinishWithColon() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class Foo {{ Object o = Fo<caret>x }}
        """);
      myFixture.completeBasic();
      myFixture.type("::");
      myFixture.checkResult("""
                              class Foo {{ Object o = Foo::<caret>x }}
                              """);
    });
  }

  @Test
  public void testNoSuggestionsAfterMethodReferenceAndDot() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    String text = """
      class Foo {{ Object o = StringBuilder::append.<caret> }}
      """;
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", text);
      assertThat(myFixture.completeBasic()).isNullOrEmpty();
      myFixture.checkResult(text);
    });
  }

  @Test
  public void test_suggest_lambda_signature() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        interface I {
          void m(int x);
        }
            
        class Test {
          public static void main(int x) {
            I i = <caret>
          }
        }""");
      LookupElement[] items = myFixture.completeBasic();
      assertEquals("x1 -> {}", NormalCompletionFixture.renderElement(items[0]).getItemText());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_lambda_signature_duplicate_parameter_name() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        import java.util.function.Function;
            
        public class E {
            public static void main(String[] args) {
                Observable.just(new InterestingClass())
                        .doOnNext(interestingClass -> doSomething())
                        .doOnNext(inter<caret>)
            }
        }
            
        class Observable {
             static <T> Smth<T> just(T t) { }
        }
            
        class Smth<T> {
            Smth<T> doOnNext(Function<T, Void> fun) { }
        }
            
        class InterestingClass {}
        """);
      LookupElement[] items = myFixture.completeBasic();
      assertEquals("interestingClass -> {}", NormalCompletionFixture.renderElement(items[0]).getItemText());
    });
  }

  @Test
  public void test_suggest_this_method_references() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        interface I {
          void m(int x);
        }
            
        class Test {
          {
            I i = <caret>
          }
          void bar(int i) {}
        }""");
      LookupElement[] items = myFixture.completeBasic();
      assertTrue(ContainerUtil.exists(items, it -> NormalCompletionFixture.renderElement(it).getItemText().equals("x -> {}")));
      assertTrue(ContainerUtil.exists(items, it -> NormalCompletionFixture.renderElement(it).getItemText().contains("this::bar")));
    });
  }

  @Test
  public void test_suggest_receiver_method_reference() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        class MethodRef {
            
            private void m() {
                zoo(<caret>);
            }
            
            interface I<T> {
                void foo(MethodRef m, T a);
            }
            
            void boo(String s, int unrelated) {
            }
            
            void boo(String s) {
            }
            
            void zoo(I<String> i) {}
        }
        """);
      LookupElement[] items = myFixture.completeBasic();
      assertTrue(ContainerUtil.exists(items, it -> NormalCompletionFixture.renderElement(it).getItemText().contains("MethodRef::boo")));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_suggest_receiver_method_reference_for_generic_methods() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        import java.util.*;
        import java.util.stream.Stream;
        class MethodRef {
            
            private void m(Stream<Map.Entry<String, Integer>> stream) {
                stream.map(<caret>);
            }
        }
        """);
      LookupElement[] items = myFixture.completeBasic();
      assertTrue(ContainerUtil.exists(items, it -> NormalCompletionFixture.renderElement(it).getItemText().contains("Entry::getKey")));
    });
  }

  @Test
  public void test_constructor_ref() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        interface Foo9 {
          Bar test(int p);
        }
            
        class Bar {
          public Bar(int p) {}
        }
            
        class Test88 {
          {
            Foo9 f = Bar::<caret>;
          }
        }
        """);
      myFixture.completeBasic();
      myFixture.type("\n");

      myFixture.checkResult("""
                              interface Foo9 {
                                Bar test(int p);
                              }
                                  
                              class Bar {
                                public Bar(int p) {}
                              }
                                  
                              class Test88 {
                                {
                                  Foo9 f = Bar::new;
                                }
                              }
                              """);
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testInheritorConstructorRef() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "ArrayList::new", "ArrayList", "CopyOnWriteArrayList::new");

      LookupElement constructorRef = myFixture.getLookupElements()[0];
      LookupElementPresentation p = NormalCompletionFixture.renderElement(constructorRef);
      assertEquals(" (java.util)", p.getTailText());
      assertTrue(p.getTailFragments().get(0).isGrayed());

      assertEquals(ArrayList.class.getName(), ((PsiClass)constructorRef.getPsiElement()).getQualifiedName());
    });
  }

  @Test
  public void test_constructor_ref_without_start() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        interface Foo9 {
          Bar test(int p);
        }
            
        class Bar {
          public Bar(int p) {}
        }
            
        class Test88 {
          {
            Foo9 f = <caret>;
          }
        }
        """);
      LookupElement[] items = myFixture.completeBasic();
      assertTrue(ContainerUtil.exists(items, it -> NormalCompletionFixture.renderElement(it).getItemText().contains("Bar::new")));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_new_array_ref() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        interface Foo9<T> {
          T test(int p);
        }
            
        class Test88 {
          {
            Foo9<String[]> f = <caret>;
          }
        }
        """);
      LookupElement[] items = myFixture.completeBasic();
      assertTrue(ContainerUtil.exists(items, it -> NormalCompletionFixture.renderElement(it).getItemText().contains("String[]::new")));
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCollectorsToList() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), it -> it.getLookupString().contains("toList")));
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testStaticallyImportedCollectorsToList() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), it -> it.getLookupString().contains("collect(toList())")));
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testAllCollectors() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      assertEquals(myFixture.getLookupElementStrings(),
                   List.of("collect", "collect", "collect(Collectors.toCollection())", "collect(Collectors.toList())", "collect(Collectors.toSet())"));
      completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), it -> it.getLookupString().contains("toCollection")));
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCollectorsJoining() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCollectorsToSet() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      assertTrue(ContainerUtil.exists(completion.get().getItems(), it -> it.getLookupString().equals("collect(Collectors.joining())")));
      assertTrue(ContainerUtil.exists(completion.get().getItems(), it -> it.getLookupString().equals("collect(Collectors.toList())")));
      completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), it -> it.getLookupString().equals("collect(Collectors.toSet())")));
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCollectorsInsideCollect() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "toCollection", "toList", "toSet");
      completion.get().selectItem(completion.get().getItems()[1]);
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCollectorsJoiningInsideCollect() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoExplicitTypeArgsInTernary() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), it -> it.getLookupString().contains("empty")));
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testCallBeforeLambda() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLambdaInAmbiguousCall() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "toString", "getClass");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLambdaInAmbiguousConstructorCall() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      completion.get().selectItem(ContainerUtil.find(completion.get().getItems(), it -> it.getLookupString().contains("Empty")));
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testLambdaWithSuperWildcardInAmbiguousCall() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "substring", "substring", "subSequence");
    });
  }

  @Test
  public void testUnexpectedLambdaInAmbiguousCall() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testNoCollectorsInComment() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testNoContinueInsideLambdaInLoop() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoSemicolonAfterVoidMethodInLambda() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.type("l\t");
      checkResultByFileName();
    });
  }

  @Test
  public void testFinishMethodReferenceWithColon() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.type(":");
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferLocalsOverMethodRefs() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "psiElement1 -> ", "psiElement", "getParent", "PsiElement");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testStaticallyImportedFromInterface() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
         package pkg;
         public interface Point {
             static Point point(double x, double y) {}
         }""");
      completion.get().configureByTestName();
      myFixture.type("\n");
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for overriding method completion)")
  public void testOverrideMethodAsDefault() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      assertEquals("default void run", NormalCompletionFixture.renderElement(myFixture.getLookupElements()[0]).getItemText());
      myFixture.type("\t");
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testChainedMethodReference() {
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.Full
  public void testChainedMethodReferenceWithNoPrefix() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package bar; public class Strings {}");
      myFixture.addClass("package foo; public class Strings { public static void goo() {} }");
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "Strings::goo");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testOnlyAccessibleClassesInChainedMethodReference() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      LookupElementPresentation p = NormalCompletionFixture.renderElement(assertThat(myFixture.getLookupElements()).singleElement().actual());
      assertEquals("Entry::getKey", p.getItemText());
      assertEquals(" java.util.Map", p.getTailText());
      assertNull(p.getTypeText());
    });
  }

  @Test
  public void testPreferVariableToLambda() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "output", "out -> ");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferLambdaToConstructorReference() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "() -> ", "Exception::new");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferLambdaToTooGenericLocalVariables() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "(foo, foo2) -> ");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferLambdaToRecentSelections() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "String");
      myFixture.type("\n str;\n");// select 'String'
      myFixture.type("s.reduce(");
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "(foo, foo2) -> ", "s", "str", "String");
    });
  }

  private void checkResultByFileName() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    completion.get().checkResultByFile(myFixture.getTestName(false) + "_after.java");
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_intersection_type_members() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "import java.util.*; class F { { (true ? new LinkedList<>() : new ArrayList<>()).<caret> }}");
      myFixture.completeBasic();
      assertFalse(myFixture.getLookupElementStrings().contains("finalize"));
    });
  }

  @Test
  public void test_do_not_suggest_inaccessible_methods() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "import java.util.*; class F { { new ArrayList<String>().forEach(O<caret>) }}");
      myFixture.completeBasic();
      assertFalse(myFixture.getLookupElementStrings().contains("finalize"));
    });
  }

  @Test
  public void test_only_importable_suggestions_in_import() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package com.foo; public class Comments { public static final int B = 2; }");
      myFixture.configureByText("a.java", "import com.<caret>x.y;\nimport static java.util.stream.Collectors.joining;");
      myFixture.completeBasic();
      assertEquals(List.of("foo"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void test_no_overloaded_method_reference_duplicates() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class C { { Runnable r = this::wa<caret>x; } }");
      myFixture.completeBasic();
      assertEquals(List.of("wait"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testStreamMethodsOnCollection() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "filter");
      assertEquals("filter", NormalCompletionFixture.renderElement(myFixture.getLookupElements()[0]).getItemText());

      myFixture.type("ma");
      myFixture.assertPreferredCompletionItems(0, "map", "mapToDouble");
      assertEquals("stream().map", NormalCompletionFixture.renderElement(myFixture.getLookupElements()[0]).getItemText());

      myFixture.type("\n");
      checkResultByFileName();
    });
  }

  @Test
  public void testSuggestOnlyAccessibleStreamMethod() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testStreamMethodsOnArray() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "length", "clone");
      assertFalse(ContainerUtil.exists(myFixture.getLookupElements(), it -> NormalCompletionFixture.renderElement(it).getItemText().contains("stream().toString")));

      myFixture.type("ma");
      myFixture.assertPreferredCompletionItems(0, "map", "mapToDouble");

      myFixture.type("\n");
      checkResultByFileName();
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferConstructorReferenceOfExpectedType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "new");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testPreferQualifiedMethodReferenceOfExpectedType() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configureByTestName();
      myFixture.assertPreferredCompletionItems(0, "aDouble -> ", "doubleValue");
    });
  }

  @Test
  public void testNoStreamSuggestionsOnBrokenCode() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testNoStreamSuggestionsInMethodReference() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doAntiTest()); }

  @Test
  public void testNoCloneSuggestionOnStream() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java",
                                "import java.util.stream.*;class Cls {{Stream.of(\"a,b,c\").flatMap(l -> l.split(\",\").stre<caret>)}}");
      LookupElement[] elements = myFixture.completeBasic();
      assert elements.length == 0;
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testToLowerCase() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class C { String s = \"hello\".toUp<caret> }");
      myFixture.completeBasic();
      assertEquals(List.of("toUpperCase(Locale.ROOT)", "toUpperCase", "toUpperCase"), myFixture.getLookupElementStrings());
      myFixture.type("\n");
      myFixture.checkResult("""
        import java.util.Locale;

        class C { String s = "hello".toUpperCase(Locale.ROOT) }""");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testGetBytes() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", "class C { byte[] s = \"hello\".getB<caret> }");
      myFixture.completeBasic();
      assertEquals(List.of("getBytes(StandardCharsets.UTF_8)", "getBytes", "getBytes", "getBytes", "getBytes"), myFixture.getLookupElementStrings());
      myFixture.type("\n");
      myFixture.checkResult("class C { byte[] s = \"hello\".getBytes(java.nio.charset.StandardCharsets.UTF_8) }");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testDotAfterMethodRef() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        import java.util.HashSet;
        import java.util.stream.Collectors;
      
        class Scratch {
            public static void main(String[] args) {
              HashSet<String> set = new HashSet<>();
              set
                .stream()
                .filter(String::isEmpty.<caret>)
                .collect(Collectors.joining());
            }
        }""");
      myFixture.completeBasic();
      assertEquals(List.of(), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testQueuePeek() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("a.java", """
        import java.util.Queue;
      
        class X {
          void test(Queue<String> queue) {
            queue.pe<caret>
          }
        }
        """);
      myFixture.completeBasic();
      assertEquals(List.of("peek", "peek"), myFixture.getLookupElementStrings());
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNewClassGenericImport() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", "import java.util.List;class UseClass {void test() {List<String> list = <caret>}}");
      LookupElement[] elements = myFixture.completeBasic();
      LookupElement arrayList = ContainerUtil.find(elements, e -> e.getLookupString().equals("new ArrayList"));
      assertNotNull(arrayList);
      LookupElementPresentation presentation = NormalCompletionFixture.renderElement(arrayList);
      assertEquals("new ArrayList", presentation.getItemText());
      assertEquals("<>()", presentation.getTailText());
      assertEquals("ArrayList<String>", presentation.getTypeText());

      completion.get().selectItem(arrayList);
      myFixture.checkResult("""
          import java.util.ArrayList;
          import java.util.List;class UseClass {void test() {List<String> list = new ArrayList<>()}}""");
    });
  }

  @Test
  @NeedsIndex.SmartMode(reason = "Resolve-based name suggestions are not shown in dumb mode")
  public void testNameFromRequireNonNullArg() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        import java.util.Objects;
      
        public class Test {
          void test() {
            String su<caret> = Objects.requireNonNull(superMessage(), "Ooops");
          }
      
          native String superMessage();
        }""");
      myFixture.complete(CompletionType.BASIC, 0);
      myFixture.type("\n");
      myFixture.checkResult("""
                              import java.util.Objects;
                            
                              public class Test {
                                void test() {
                                  String superMessage = Objects.requireNonNull(superMessage(), "Ooops");
                                }

                                native String superMessage();
                              }""");
    });
  }

}
