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
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.testFramework.LightProjectDescriptor
/**
 * @author anna
 */
class Normal8CompletionTest extends LightFixtureCompletionTestCase {
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

    void boo(String s) {
    }

    void zoo(I<String> i) {}
}
"""
    def items = myFixture.completeBasic()
    assert items.find {LookupElementPresentation.renderElement(it).itemText.contains('MethodRef::boo')}
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
    myFixture.assertPreferredCompletionItems 0, 'ArrayList::new', 'ArrayList'
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
    myFixture.assertPreferredCompletionItems 0, 'collect', 'collect', 'collect(Collectors.toCollection())', 'collect(Collectors.toList())', 'collect(Collectors.toSet())'
    selectItem(myItems.find { it.lookupString.contains('toCollection') })
    checkResultByFileName()
  }

  void testCollectorsToSet() {
    configureByTestName()
    selectItem(myItems.find { it.lookupString.contains('toSet') })
    checkResultByFileName()
  }

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

  void testPreferVariableToLambda() {
    configureByTestName()
    myFixture.assertPreferredCompletionItems 0, 'output', 'out -> '
  }

  private checkResultByFileName() {
    checkResultByFile(getTestName(false) + "_after.java")
  }
}