// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.JavaLambdaParameterTypeHintsProvider
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import org.intellij.lang.annotations.Language

class JavaLambdaParameterTypeHintsProviderTest : DeclarativeInlayHintsProviderTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = LightJavaCodeInsightFixtureTestCase.JAVA_17

  private val functionInterface = """
    public interface BiFunction<T, U> {
        U apply(T t);
    }

    class FooClass {
        <T, U> U foo(BiFunction<T, U> func, T a){
            return func.apply(a);
        }
    }"""

  fun `test parameter not shown`() {
    val text = """
        class Demo {
            private static void pure(int x, int y) {
                var items = Arrays.asList(1, 2, 3, 4, 5);
                var product = items.stream().reduce(1, (a, b) -> a * b);
            }
        }"""
    testAnnotations(text)
  }

  fun `test basic parameters`() {
    val text = """
        $functionInterface

        class Demo {
            private static void pure(int x, String s) {
                FooClass obj = new FooClass();
                obj.foo(/*<# [java.lang.String:java.fqn.class]String #>*/a -> {}, s);
                obj.foo(/*<# [java.lang.Integer:java.fqn.class]Integer #>*/a -> {}, x);
            }
        }"""
    testAnnotations(text)
  }

  fun `test integer list parameter`() {
    val listClass = """
    class ListClass {
            List<Integer> foo(BiFunction<List<Integer>, List<Integer>> func, List<Integer> a){
                return func.apply(a);
            }
    }"""

    val text = """
        $functionInterface
        $listClass
        
        class Demo {
            private static void pure(List<Integer> list) {
                ListClass obj = new ListClass();
                obj.foo(/*<# List|<|[java.lang.Integer:java.fqn.class]Integer|> #>*/a -> {}, list);
            }
        }"""
    testAnnotations(text)
  }

  fun `test generic class parameter`() {
    val genericClass = """
    class GenericLongClass<T, U> {
        GenericLongClass(T first, U second) {}
    }"""

    val text = """
        $functionInterface
        $genericClass

        class Demo {
            private static void pure(GenericLongClass<String, Integer> firstPerson, GenericLongClass<Integer, GenericLongClass<String,String>> secondPerson) {
                FooClass obj = new FooClass();
                obj.foo(/*<# [GenericLongClass:java.fqn.class]GenericLongClass|<|[java.lang.String:java.fqn.class]String|, |[java.lang.Integer:java.fqn.class]Integer|> #>*/a -> {}, firstPerson);
                obj.foo(/*<# [GenericLongClass:java.fqn.class]GenericLongClass|<|[java.lang.Integer:java.fqn.class]Integer|, |[GenericLongClass:java.fqn.class]GenericLongClass|<...>|> #>*/a -> {}, secondPerson);
            }
        }"""
    testAnnotations(text)
  }


  private fun testAnnotations(@Language("Java") text: String) {
    doTestProvider("A.java", text, JavaLambdaParameterTypeHintsProvider(), )
  }
}