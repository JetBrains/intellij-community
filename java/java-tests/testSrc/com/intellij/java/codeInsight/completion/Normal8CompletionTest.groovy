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
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiClass
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import com.intellij.util.containers.ContainerUtil

/**
 * @author anna
 */
class Normal8CompletionTest extends NormalCompletionTestCase {
  final LightProjectDescriptor projectDescriptor = JAVA_8
  final String basePath = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/lambda/completion/normal/"

  void testSelfStaticsOnly() {
    configureByFile("SelfStaticsOnly.java")
    assertStringItems("ba", "bar")
  }

  void testFinishWithColon() {
    myFixture.configureByText "a.java", """
class Foo {{ Object o = Fo<caret>x }}
"""
    myFixture.completeBasic()
    myFixture.type('::')
    myFixture.checkResult """
class Foo {{ Object o = Foo::<caret>x }}
"""
  }

  void testNoSuggestionsAfterMethodReferenceAndDot() {
    String text = """
class Foo {{ Object o = StringBuilder::append.<caret> }}
"""
    myFixture.configureByText "a.java", text
    assertEmpty(myFixture.completeBasic())
    myFixture.checkResult(text)
  }

  void "test suggest lambda signature"() {
    myFixture.configureByText "a.java", """
interface I {
  void m(int x);
}

class Test {
  public static void main(int x) {
    I i = <caret>
  }
}"""
    def items = myFixture.completeBasic()
    assert renderElement(items[0]).itemText == 'x1 -> {}'
  }

  @NeedsIndex.ForStandardLibrary
  void "test lambda signature duplicate parameter name"() {
    myFixture.configureByText "a.java", """
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
"""
    def items = myFixture.completeBasic()
    assert renderElement(items[0]).itemText == 'interestingClass -> {}'
  }

  void "test suggest this method references"() {
    myFixture.configureByText "a.java", """
interface I {
  void m(int x);
}

class Test {
  {
    I i = <caret>
  }
  void bar(int i) {}
}"""
    def items = myFixture.completeBasic()
    assert items.any { renderElement(it).itemText == 'x -> {}' }
    assert items.any { renderElement(it).itemText.contains('this::bar') }
  }

  void "test suggest receiver method reference"() {
    myFixture.configureByText "a.java", """
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
"""
    def items = myFixture.completeBasic()
    assert items.find {renderElement(it).itemText.contains('MethodRef::boo')}
  }

  @NeedsIndex.ForStandardLibrary
  void "test suggest receiver method reference for generic methods"() {
    myFixture.configureByText "a.java", """
import java.util.*;
import java.util.stream.Stream;
class MethodRef {

    private void m(Stream<Map.Entry<String, Integer>> stream) {
        stream.map(<caret>);
    }
}
"""
    def items = myFixture.completeBasic()
    assert items.find {renderElement(it).itemText.contains('Entry::getKey')}
  }

  void "test constructor ref"() {
    myFixture.configureByText "a.java", """
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
"""
    myFixture.completeBasic()
    myFixture.type('\n')

    myFixture.checkResult """
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
"""
  }

  @NeedsIndex.ForStandardLibrary
  void testInheritorConstructorRef() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'ArrayList::new', 'ArrayList', 'CopyOnWriteArrayList::new'

    def constructorRef = myFixture.lookupElements[0]
    def p = renderElement(constructorRef)
    assert p.tailText == ' (java.util)'
    assert p.tailFragments[0].grayed

    assert (constructorRef.psiElement as PsiClass).qualifiedName == ArrayList.name
  }

  void "test constructor ref without start"() {
    myFixture.configureByText "a.java", """
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
"""
    def items = myFixture.completeBasic()
    assert items.find {renderElement(it).itemText.contains('Bar::new')}
  }

  @NeedsIndex.ForStandardLibrary
  void "test new array ref"() {
    myFixture.configureByText "a.java", """
interface Foo9<T> {
  T test(int p);
}

class Test88 {
  {
    Foo9<String[]> f = <caret>;
  }
}
"""
    def items = myFixture.completeBasic()
    assert items.find {renderElement(it).itemText.contains('String[]::new')}
  }

  @NeedsIndex.ForStandardLibrary
  void testCollectorsToList() {
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('toList') })
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testStaticallyImportedCollectorsToList() {
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('collect(toList())') })
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testAllCollectors() {
    configureByTestName()
    assert myFixture.lookupElementStrings == ['collect', 'collect', 'collect(Collectors.toCollection())', 'collect(Collectors.toList())', 'collect(Collectors.toSet())']
    selectItem(myItems.find { it.lookupString.contains('toCollection') })
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testCollectorsJoining() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testCollectorsToSet() {
    configureByTestName()
    assert myItems.find { it.lookupString == 'collect(Collectors.joining())' } != null 
    assert myItems.find { it.lookupString == 'collect(Collectors.toList())' } != null 
    selectItem(myItems.find { it.lookupString == 'collect(Collectors.toSet())' })
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testCollectorsInsideCollect() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'toCollection', 'toList', 'toSet'
    selectItem(myItems[1])
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testCollectorsJoiningInsideCollect() { doTest() }

  @NeedsIndex.ForStandardLibrary
  void testNoExplicitTypeArgsInTernary() {
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('empty') })
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testCallBeforeLambda() {
    configureByTestName()
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testLambdaInAmbiguousCall() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems(0, 'toString', 'getClass')
  }

  @NeedsIndex.ForStandardLibrary
  void testLambdaInAmbiguousConstructorCall() {
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('Empty') })
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testLambdaWithSuperWildcardInAmbiguousCall() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems(0, 'substring', 'substring', 'subSequence')
  }

  void testUnexpectedLambdaInAmbiguousCall() { doAntiTest() }

  void testNoCollectorsInComment() { doAntiTest() }

  void testNoContinueInsideLambdaInLoop() { doAntiTest() }

  @NeedsIndex.ForStandardLibrary
  void testNoSemicolonAfterVoidMethodInLambda() {
    configureByTestName()
    myFixture.type('l\t')
    checkResultByFileName()
  }

  void testFinishMethodReferenceWithColon() {
    configureByTestName()
    myFixture.type(':')
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferLocalsOverMethodRefs() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE)
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, "psiElement1 -> ", "psiElement", "getParent", "PsiElement"
  }

  @NeedsIndex.Full
  void testStaticallyImportedFromInterface() {
    myFixture.addClass("package pkg;\n" +
                       "public interface Point {\n" +
                       "    static Point point(double x, double y) {}\n" +
                       "}")
    configureByTestName()
    myFixture.type('\n')
    checkResultByFileName()
  }

  @NeedsIndex.SmartMode(reason = "JavaGenerateMemberCompletionContributor.fillCompletionVariants works in smart mode only (for overriding method completion)")
  void testOverrideMethodAsDefault() {
    configureByTestName()
    assert renderElement(myFixture.lookupElements[0]).itemText == 'default void run'
    myFixture.type('\t')
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testChainedMethodReference() {
    configureByTestName()
    checkResultByFileName()
  }

  @NeedsIndex.Full
  void testChainedMethodReferenceWithNoPrefix() {
    myFixture.addClass("package bar; public class Strings {}")
    myFixture.addClass("package foo; public class Strings { public static void goo() {} }")
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'Strings::goo'
  }

  @NeedsIndex.ForStandardLibrary
  void testOnlyAccessibleClassesInChainedMethodReference() {
    configureByTestName()
    def p = renderElement(assertOneElement(myFixture.lookupElements))
    assert p.itemText == 'Entry::getKey'
    assert p.tailText == ' java.util.Map'
    assert !p.typeText
  }

  void testPreferVariableToLambda() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'output', 'out -> '
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferLambdaToConstructorReference() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, '() -> ', 'Exception::new'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferLambdaToTooGenericLocalVariables() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, '(foo, foo2) -> '
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferLambdaToRecentSelections() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'String'
    myFixture.type('\n str;\n') // select 'String'
    myFixture.type('s.reduce(')
    myFixture.completeBasic()
    myFixture.assertPreferredCompletionItems 0, '(foo, foo2) -> ', 's', 'str', 'String'
  }

  private checkResultByFileName() {
    checkResultByFile(getTestName(false) + "_after.java")
  }

  void "test intersection type members"() {
    myFixture.configureByText 'a.java',
                              'import java.util.*; class F { { (true ? new LinkedList<>() : new ArrayList<>()).<caret> }}'
    myFixture.completeBasic()
    assert !('finalize' in myFixture.lookupElementStrings)
  }

  void "test do not suggest inaccessible methods"() {
    myFixture.configureByText 'a.java',
                              'import java.util.*; class F { { new ArrayList<String>().forEach(O<caret>) }}'
    myFixture.completeBasic()
    assert !('finalize' in myFixture.lookupElementStrings)
  }

  void "test only importable suggestions in import"() {
    CodeInsightSettings.getInstance().setCompletionCaseSensitive(CodeInsightSettings.NONE)
    myFixture.addClass("package com.foo; public class Comments { public static final int B = 2; }")
    myFixture.configureByText("a.java", "import com.<caret>x.y;\n" +
                                        "import static java.util.stream.Collectors.joining;")
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['foo']
  }

  @NeedsIndex.ForStandardLibrary
  void "test no overloaded method reference duplicates"() {
    myFixture.configureByText 'a.java', 'class C { { Runnable r = this::wa<caret>x; } }'
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['wait']
  }

  @NeedsIndex.ForStandardLibrary
  void testStreamMethodsOnCollection() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'filter'
    assert renderElement(myFixture.lookupElements[0]).itemText == 'filter'

    myFixture.type('ma')
    myFixture.assertPreferredCompletionItems 0, 'map', 'mapToDouble'
    assert renderElement(myFixture.lookupElements[0]).itemText == 'stream().map'

    myFixture.type('\n')
    checkResultByFileName()
  }

  void testSuggestOnlyAccessibleStreamMethod() { doAntiTest() }

  @NeedsIndex.ForStandardLibrary
  void testStreamMethodsOnArray() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'length', 'clone'
    assert !myFixture.lookupElements.find { renderElement(it).itemText.contains('stream().toString') }

    myFixture.type('ma')
    myFixture.assertPreferredCompletionItems 0, 'map', 'mapToDouble'

    myFixture.type('\n')
    checkResultByFileName()
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferConstructorReferenceOfExpectedType() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'new'
  }

  @NeedsIndex.ForStandardLibrary
  void testPreferQualifiedMethodReferenceOfExpectedType() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'aDouble -> ', 'doubleValue'
  }

  void testNoStreamSuggestionsOnBrokenCode() { doAntiTest() }

  void testNoStreamSuggestionsInMethodReference() { doAntiTest() }

  void testNoCloneSuggestionOnStream() {
    myFixture.configureByText("a.java", 'import java.util.stream.*;' +
                                        'class Cls {{Stream.of("a,b,c").flatMap(l -> l.split(",").stre<caret>)}}')
    def elements = myFixture.completeBasic()
    assert elements.length == 0
  }
  
  @NeedsIndex.ForStandardLibrary
  void testToLowerCase() {
    myFixture.configureByText 'a.java', 'class C { String s = "hello".toUp<caret> }'
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['toUpperCase(Locale.ROOT)', 'toUpperCase', 'toUpperCase']
    myFixture.type('\n')
    myFixture.checkResult('import java.util.Locale;\n\n' +
            'class C { String s = "hello".toUpperCase(Locale.ROOT) }')
  }

  @NeedsIndex.ForStandardLibrary
  void testGetBytes() {
    myFixture.configureByText 'a.java', 'class C { byte[] s = "hello".getB<caret> }'
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['getBytes(StandardCharsets.UTF_8)', 'getBytes', 'getBytes', 'getBytes', 'getBytes']
    myFixture.type('\n')
    myFixture.checkResult('class C { byte[] s = "hello".getBytes(java.nio.charset.StandardCharsets.UTF_8) }')
  }
  
  @NeedsIndex.ForStandardLibrary
  void testDotAfterMethodRef() {
    myFixture.configureByText 'a.java', """import java.util.HashSet;
import java.util.stream.Collectors;

class Scratch {
    public static void main(String[] args) {
      HashSet<String> set = new HashSet<>();
      set
        .stream()
        .filter(String::isEmpty.<caret>)
        .collect(Collectors.joining());
    }
}"""
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == []
  }

  @NeedsIndex.ForStandardLibrary
  void testQueuePeek() {
    myFixture.configureByText 'a.java', """
import java.util.Queue;

class X {
  void test(Queue<String> queue) {
    queue.pe<caret>
  }
}
"""
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ["peek", "peek"]
  }


  @NeedsIndex.ForStandardLibrary
  void testNewClassGenericImport() {
    myFixture.configureByText("Test.java", "import java.util.List;class UseClass {void test() {List<String> list = <caret>}}")
    LookupElement[] elements = myFixture.completeBasic()
    LookupElement arrayList = ContainerUtil.find(elements, { e -> e.getLookupString().equals("new ArrayList") })
    assertNotNull(arrayList)
    LookupElementPresentation presentation = renderElement(arrayList)
    assertEquals("new ArrayList", presentation.getItemText())
    assertEquals("<>()", presentation.getTailText())
    assertEquals("ArrayList<String>", presentation.getTypeText())

    selectItem(arrayList)
    myFixture.checkResult("import java.util.ArrayList;\n" +
                          "import java.util.List;class UseClass {void test() {List<String> list = new ArrayList<>()}}")
  }


}