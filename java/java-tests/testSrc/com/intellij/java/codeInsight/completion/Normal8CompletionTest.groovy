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
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiClass
import com.intellij.testFramework.LightProjectDescriptor
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
    assert LookupElementPresentation.renderElement(items[0]).itemText == 'x1 -> {}'
  }

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
    assert LookupElementPresentation.renderElement(items[0]).itemText == 'interestingClass -> {}'
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
    assert items.any { LookupElementPresentation.renderElement(it).itemText == 'x -> {}' }
    assert items.any { LookupElementPresentation.renderElement(it).itemText.contains('this::bar') }
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
    assert items.find {LookupElementPresentation.renderElement(it).itemText.contains('MethodRef::boo')}
  }

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
    assert items.find {LookupElementPresentation.renderElement(it).itemText.contains('Entry::getKey')}
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

  void testInheritorConstructorRef() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'ArrayList::new', 'ArrayList', 'CopyOnWriteArrayList::new'

    def constructorRef = myFixture.lookupElements[0]
    def p = LookupElementPresentation.renderElement(constructorRef)
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
    assert items.find {LookupElementPresentation.renderElement(it).itemText.contains('Bar::new')}
  }

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
    assert items.find {LookupElementPresentation.renderElement(it).itemText.contains('String[]::new')}
  }

  void testCollectorsToList() {
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('toList') })
    checkResultByFileName()
  }

  void testStaticallyImportedCollectorsToList() {
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('collect(toList())') })
    checkResultByFileName()
  }

  void testAllCollectors() {
    configureByTestName()
    assert myFixture.lookupElementStrings == ['collect', 'collect', 'collect(Collectors.toCollection())', 'collect(Collectors.toList())', 'collect(Collectors.toSet())']
    selectItem(myItems.find { it.lookupString.contains('toCollection') })
    checkResultByFileName()
  }

  void testCollectorsJoining() { doTest() }

  void testCollectorsToSet() {
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('toSet') })
    checkResultByFileName()
  }

  void testCollectorsInsideCollect() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'toCollection', 'toList', 'toSet'
    selectItem(myItems[1])
    checkResultByFileName()
  }

  void testCollectorsJoiningInsideCollect() { doTest() }

  void testNoExplicitTypeArgsInTernary() {
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('empty') })
    checkResultByFileName()
  }

  void testCallBeforeLambda() {
    configureByTestName()
    checkResultByFileName()
  }

  void testLambdaInAmbiguousCall() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems(0, 'toString', 'wait')
  }

  void testLambdaInAmbiguousConstructorCall() {
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('Empty') })
    checkResultByFileName()
  }

  void testLambdaWithSuperWildcardInAmbiguousCall() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems(0, 'substring', 'substring', 'subSequence')
  }

  void testUnexpectedLambdaInAmbiguousCall() { doAntiTest() }

  void testNoCollectorsInComment() { doAntiTest() }

  void testNoContinueInsideLambdaInLoop() { doAntiTest() }

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

  void testPreferLocalsOverMethodRefs() {
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, "psiElement1 -> ", "psiElement", "getParent", "PsiElement"
  }

  void testStaticallyImportedFromInterface() {
    myFixture.addClass("package pkg;\n" +
                       "public interface Point {\n" +
                       "    static Point point(double x, double y) {}\n" +
                       "}")
    configureByTestName()
    myFixture.type('\n')
    checkResultByFileName()
  }

  void testOverrideMethodAsDefault() {
    configureByTestName()
    assert LookupElementPresentation.renderElement(myFixture.lookupElements[0]).itemText == 'default void run'
    myFixture.type('\t')
    checkResultByFileName()
  }

  void testChainedMethodReference() {
    configureByTestName()
    checkResultByFileName()
  }

  void testChainedMethodReferenceWithNoPrefix() {
    myFixture.addClass("package bar; public class Strings {}")
    myFixture.addClass("package foo; public class Strings { public static void goo() {} }")
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'Strings::goo'
  }

  void testOnlyAccessibleClassesInChainedMethodReference() {
    configureByTestName()
    def p = LookupElementPresentation.renderElement(assertOneElement(myFixture.lookupElements))
    assert p.itemText == 'Entry::getKey'
    assert p.tailText == ' java.util.Map'
    assert !p.typeText
  }

  void testPreferVariableToLambda() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'output', 'out -> '
  }

  void testPreferLambdaToConstructorReference() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, '() -> ', 'Exception::new'
  }

  void testPreferLambdaToTooGenericLocalVariables() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, '(foo, foo2) -> '
  }

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
    CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = CodeInsightSettings.NONE
    myFixture.addClass("package com.foo; public class Comments { public static final int B = 2; }")
    myFixture.configureByText("a.java", "import com.<caret>x.y;\n" +
                                        "import static java.util.stream.Collectors.joining;")
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['foo']
  }

  void "test no overloaded method reference duplicates"() {
    myFixture.configureByText 'a.java', 'class C { { Runnable r = this::wa<caret>x; } }'
    myFixture.completeBasic()
    assert myFixture.lookupElementStrings == ['wait']
  }
}