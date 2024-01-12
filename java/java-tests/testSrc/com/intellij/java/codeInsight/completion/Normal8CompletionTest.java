package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author anna
 */
public class Normal8CompletionTest extends NormalCompletionTestCase {
  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  public final String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/lambda/completion/normal/";
  }

  public void testSelfStaticsOnly() {
    configureByFile("SelfStaticsOnly.java");
    assertStringItems("ba", "bar");
  }

  public void testFinishWithColon() {
    myFixture.configureByText("a.java", """
      class Foo {{ Object o = Fo<caret>x }}
      """);
    myFixture.completeBasic();
    myFixture.type("::");
    myFixture.checkResult("""
                            class Foo {{ Object o = Foo::<caret>x }}
                            """);
  }

  public void testNoSuggestionsAfterMethodReferenceAndDot() {
    String text = """
      class Foo {{ Object o = StringBuilder::append.<caret> }}
      """;
    myFixture.configureByText("a.java", text);
    UsefulTestCase.assertEmpty(myFixture.completeBasic());
    myFixture.checkResult(text);
  }

  public void test_suggest_lambda_signature() {
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
    assertEquals("x1 -> {}", renderElement(items[0]).getItemText());
  }

  @NeedsIndex.ForStandardLibrary
  public void test_lambda_signature_duplicate_parameter_name() {
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
    assertEquals("interestingClass -> {}", renderElement(items[0]).getItemText());
  }

  public void test_suggest_this_method_references() {
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
    assertTrue(ContainerUtil.exists(items, it -> renderElement(it).getItemText().equals("x -> {}")));
    assertTrue(ContainerUtil.exists(items, it -> renderElement(it).getItemText().contains("this::bar")));
  }

  public void test_suggest_receiver_method_reference() {
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
    assertTrue(ContainerUtil.exists(items, it -> renderElement(it).getItemText().contains("MethodRef::boo")));
  }

  @NeedsIndex.ForStandardLibrary
  public void test_suggest_receiver_method_reference_for_generic_methods() {
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
    assertTrue(ContainerUtil.exists(items, it -> renderElement(it).getItemText().contains("Entry::getKey")));
  }

  public void test_constructor_ref() {
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
  }

  @NeedsIndex.ForStandardLibrary
  public void testInheritorConstructorRef() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "ArrayList::new", "ArrayList", "CopyOnWriteArrayList::new");

    LookupElement constructorRef = myFixture.getLookupElements()[0];
    LookupElementPresentation p = renderElement(constructorRef);
    assertEquals(" (java.util)", p.getTailText());
    assertTrue(p.getTailFragments().get(0).isGrayed());

    assertEquals(ArrayList.class.getName(), ((PsiClass)constructorRef.getPsiElement()).getQualifiedName());
  }

  public void test_constructor_ref_without_start() {
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
    assertTrue(ContainerUtil.exists(items, it -> renderElement(it).getItemText().contains("Bar::new")));
  }

  @NeedsIndex.ForStandardLibrary
  public void test_new_array_ref() {
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
    assertTrue(ContainerUtil.exists(items, it -> renderElement(it).getItemText().contains("String[]::new")));
  }

  @NeedsIndex.ForStandardLibrary
  public void testCollectorsToList() {
    configureByTestName();
    selectItem(ContainerUtil.find(myItems, it -> it.getLookupString().contains("toList")));
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testStaticallyImportedCollectorsToList() {
    configureByTestName();
    selectItem(ContainerUtil.find(myItems, it -> it.getLookupString().contains("collect(toList())")));
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testAllCollectors() {
    configureByTestName();
    assertEquals(myFixture.getLookupElementStrings(),
                 List.of("collect", "collect", "collect(Collectors.toCollection())", "collect(Collectors.toList())", "collect(Collectors.toSet())"));
    selectItem(ContainerUtil.find(myItems, it -> it.getLookupString().contains("toCollection")));
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testCollectorsJoining() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testCollectorsToSet() {
    configureByTestName();
    assertTrue(ContainerUtil.exists(myItems, it -> it.getLookupString().equals("collect(Collectors.joining())")));
    assertTrue(ContainerUtil.exists(myItems, it -> it.getLookupString().equals("collect(Collectors.toList())")));
    selectItem(ContainerUtil.find(myItems, it -> it.getLookupString().equals("collect(Collectors.toSet())")));
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testCollectorsInsideCollect() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "toCollection", "toList", "toSet");
    selectItem(myItems[1]);
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testCollectorsJoiningInsideCollect() { doTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testNoExplicitTypeArgsInTernary() {
    configureByTestName();
    selectItem(ContainerUtil.find(myItems, it -> it.getLookupString().contains("empty")));
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testCallBeforeLambda() {
    configureByTestName();
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testLambdaInAmbiguousCall() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "toString", "getClass");
  }

  @NeedsIndex.ForStandardLibrary
  public void testLambdaInAmbiguousConstructorCall() {
    configureByTestName();
    selectItem(ContainerUtil.find(myItems, it -> it.getLookupString().contains("Empty")));
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testLambdaWithSuperWildcardInAmbiguousCall() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "substring", "substring", "subSequence");
  }

  public void testUnexpectedLambdaInAmbiguousCall() { doAntiTest(); }

  public void testNoCollectorsInComment() { doAntiTest(); }

  public void testNoContinueInsideLambdaInLoop() { doAntiTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testNoSemicolonAfterVoidMethodInLambda() {
    configureByTestName();
    myFixture.type("l\t");
    checkResultByFileName();
  }

  public void testFinishMethodReferenceWithColon() {
    configureByTestName();
    myFixture.type(":");
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferLocalsOverMethodRefs() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "psiElement1 -> ", "psiElement", "getParent", "PsiElement");
  }

  @NeedsIndex.Full
  public void testStaticallyImportedFromInterface() {
    myFixture.addClass("""
       package pkg;
       public interface Point {
           static Point point(double x, double y) {}
       }""");
    configureByTestName();
    myFixture.type("\n");
    checkResultByFileName();
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for overriding method completion)")
  public void testOverrideMethodAsDefault() {
    configureByTestName();
    assertEquals("default void run", renderElement(myFixture.getLookupElements()[0]).getItemText());
    myFixture.type("\t");
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testChainedMethodReference() {
    configureByTestName();
    checkResultByFileName();
  }

  @NeedsIndex.Full
  public void testChainedMethodReferenceWithNoPrefix() {
    myFixture.addClass("package bar; public class Strings {}");
    myFixture.addClass("package foo; public class Strings { public static void goo() {} }");
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "Strings::goo");
  }

  @NeedsIndex.ForStandardLibrary
  public void testOnlyAccessibleClassesInChainedMethodReference() {
    configureByTestName();
    LookupElementPresentation p = renderElement(UsefulTestCase.assertOneElement(myFixture.getLookupElements()));
    assertEquals("Entry::getKey", p.getItemText());
    assertEquals(" java.util.Map", p.getTailText());
    assertNull(p.getTypeText());
  }

  public void testPreferVariableToLambda() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "output", "out -> ");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferLambdaToConstructorReference() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "() -> ", "Exception::new");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferLambdaToTooGenericLocalVariables() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "(foo, foo2) -> ");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferLambdaToRecentSelections() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "String");
    myFixture.type("\n str;\n");// select 'String'
    myFixture.type("s.reduce(");
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "(foo, foo2) -> ", "s", "str", "String");
  }

  private void checkResultByFileName() {
    checkResultByFile(getTestName(false) + "_after.java");
  }

  public void test_intersection_type_members() {
    myFixture.configureByText("a.java", "import java.util.*; class F { { (true ? new LinkedList<>() : new ArrayList<>()).<caret> }}");
    myFixture.completeBasic();
    assertFalse(myFixture.getLookupElementStrings().contains("finalize"));
  }

  public void test_do_not_suggest_inaccessible_methods() {
    myFixture.configureByText("a.java", "import java.util.*; class F { { new ArrayList<String>().forEach(O<caret>) }}");
    myFixture.completeBasic();
    assertFalse(myFixture.getLookupElementStrings().contains("finalize"));
  }

  public void test_only_importable_suggestions_in_import() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE);
    myFixture.addClass("package com.foo; public class Comments { public static final int B = 2; }");
    myFixture.configureByText("a.java", "import com.<caret>x.y;\nimport static java.util.stream.Collectors.joining;");
    myFixture.completeBasic();
    assertEquals(List.of("foo"), myFixture.getLookupElementStrings());
  }

  @NeedsIndex.ForStandardLibrary
  public void test_no_overloaded_method_reference_duplicates() {
    myFixture.configureByText("a.java", "class C { { Runnable r = this::wa<caret>x; } }");
    myFixture.completeBasic();
    assertEquals(List.of("wait"), myFixture.getLookupElementStrings());
  }

  @NeedsIndex.ForStandardLibrary
  public void testStreamMethodsOnCollection() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "filter");
    assertEquals("filter", renderElement(myFixture.getLookupElements()[0]).getItemText());

    myFixture.type("ma");
    myFixture.assertPreferredCompletionItems(0, "map", "mapToDouble");
    assertEquals("stream().map", renderElement(myFixture.getLookupElements()[0]).getItemText());

    myFixture.type("\n");
    checkResultByFileName();
  }

  public void testSuggestOnlyAccessibleStreamMethod() { doAntiTest(); }

  @NeedsIndex.ForStandardLibrary
  public void testStreamMethodsOnArray() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "length", "clone");
    assertFalse(ContainerUtil.exists(myFixture.getLookupElements(), it -> renderElement(it).getItemText().contains("stream().toString")));

    myFixture.type("ma");
    myFixture.assertPreferredCompletionItems(0, "map", "mapToDouble");

    myFixture.type("\n");
    checkResultByFileName();
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferConstructorReferenceOfExpectedType() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "new");
  }

  @NeedsIndex.ForStandardLibrary
  public void testPreferQualifiedMethodReferenceOfExpectedType() {
    configureByTestName();
    myFixture.assertPreferredCompletionItems(0, "aDouble -> ", "doubleValue");
  }

  public void testNoStreamSuggestionsOnBrokenCode() { doAntiTest(); }

  public void testNoStreamSuggestionsInMethodReference() { doAntiTest(); }

  public void testNoCloneSuggestionOnStream() {
    myFixture.configureByText("a.java",
                              "import java.util.stream.*;class Cls {{Stream.of(\"a,b,c\").flatMap(l -> l.split(\",\").stre<caret>)}}");
    LookupElement[] elements = myFixture.completeBasic();
    assert elements.length == 0;
  }

  @NeedsIndex.ForStandardLibrary
  public void testToLowerCase() {
    myFixture.configureByText("a.java", "class C { String s = \"hello\".toUp<caret> }");
    myFixture.completeBasic();
    assertEquals(List.of("toUpperCase(Locale.ROOT)", "toUpperCase", "toUpperCase"), myFixture.getLookupElementStrings());
    myFixture.type("\n");
    myFixture.checkResult("""
      import java.util.Locale;

      class C { String s = "hello".toUpperCase(Locale.ROOT) }""");
  }

  @NeedsIndex.ForStandardLibrary
  public void testGetBytes() {
    myFixture.configureByText("a.java", "class C { byte[] s = \"hello\".getB<caret> }");
    myFixture.completeBasic();
    assertEquals(List.of("getBytes(StandardCharsets.UTF_8)", "getBytes", "getBytes", "getBytes", "getBytes"), myFixture.getLookupElementStrings());
    myFixture.type("\n");
    myFixture.checkResult("class C { byte[] s = \"hello\".getBytes(java.nio.charset.StandardCharsets.UTF_8) }");
  }

  @NeedsIndex.ForStandardLibrary
  public void testDotAfterMethodRef() {
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
  }

  @NeedsIndex.ForStandardLibrary
  public void testQueuePeek() {
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
  }

  @NeedsIndex.ForStandardLibrary
  public void testNewClassGenericImport() {
    myFixture.configureByText("Test.java", "import java.util.List;class UseClass {void test() {List<String> list = <caret>}}");
    LookupElement[] elements = myFixture.completeBasic();
    LookupElement arrayList = ContainerUtil.find(elements, e -> e.getLookupString().equals("new ArrayList"));
    assertNotNull(arrayList);
    LookupElementPresentation presentation = renderElement(arrayList);
    assertEquals("new ArrayList", presentation.getItemText());
    assertEquals("<>()", presentation.getTailText());
    assertEquals("ArrayList<String>", presentation.getTypeText());

    selectItem(arrayList);
    myFixture.checkResult("""
        import java.util.ArrayList;
        import java.util.List;class UseClass {void test() {List<String> list = new ArrayList<>()}}""");
  }
}
