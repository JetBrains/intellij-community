package com.intellij.codeInspection.tests.kotlin

import com.intellij.codeInspection.tests.CallMatcherTestBase
import com.intellij.codeInspection.tests.ULanguage
import com.siyeh.ig.callMatcher.CallMatcher

class KotlinCallMatcherTest : CallMatcherTestBase() {
  fun testInstanceMethodCall() {
    checkMatchCall(ULanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterCount(0), """
      class Foo { fun bar() { } }
      
      fun main() { Foo().bar() }
    """.trimIndent())
  }

  fun testMultipleArgumentsCall() {
    checkMatchCall(ULanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterCount(3), """
      class Foo { fun bar(x: Int, y: Int, z: Int) { } }
      
      fun main() { Foo().bar(0, 0, 0) }
    """.trimIndent())
  }

  fun testMultipleArgumentsCallDefaultArg() {
    checkMatchCall(ULanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterCount(3), """
      class Foo { fun bar(x: Int, y: Int, z: Int = 0) { }  }
      
      fun main() { Foo().bar(0, 0) }
    """.trimIndent())
  }

  fun testMultipleArgumentTypes() {
    checkMatchCall(ULanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterTypes("int", "long", "double"), """
      class Foo { fun bar(x: Int, y: Long, z: Double) { } }
      
      fun main() { Foo().bar(0, 0L, 0.0) }
    """.trimIndent())
  }

  fun testInstanceMethodReference() {
    checkMatchCallableReference(ULanguage.KOTLIN, CallMatcher.instanceCall("java.lang.String", "plus").parameterCount(1), """  
      class Foo { fun bar(arg: String.(String) -> String) { }  }
      
      class Main { fun main() { Foo().bar(String::plus) } }
    """.trimIndent())
  }

  fun testMethodReferenceArgumentTypes() {
    checkMatchCallableReference(ULanguage.KOTLIN, CallMatcher.instanceCall("java.lang.String", "plus").parameterTypes("java.lang.Object"), """  
      class Foo { fun bar(arg: String.(String) -> String) { }  }
      
      class Main { fun main() { Foo().bar(String::plus) } }
    """.trimIndent())
  }
}