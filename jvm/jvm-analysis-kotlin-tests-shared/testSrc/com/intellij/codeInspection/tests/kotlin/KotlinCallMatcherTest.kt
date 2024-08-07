package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.CallMatcherTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinCallMatcherTest : CallMatcherTestBase(), KotlinPluginModeProvider {
  fun testInstanceMethodCall() {
    checkMatchCall(JvmLanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterCount(0), """
      class Foo { fun bar() { } }
      
      fun main() { Foo().bar() }
    """.trimIndent())
  }

  fun testMultipleArgumentsCall() {
    checkMatchCall(JvmLanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterCount(3), """
      class Foo { fun bar(x: Int, y: Int, z: Int) { } }
      
      fun main() { Foo().bar(0, 0, 0) }
    """.trimIndent())
  }

  fun testMultipleArgumentsCallDefaultArg() {
    checkMatchCall(JvmLanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterCount(3), """
      class Foo { fun bar(x: Int, y: Int, z: Int = 0) { }  }
      
      fun main() { Foo().bar(0, 0) }
    """.trimIndent())
  }

  fun testMultipleArgumentTypes() {
    checkMatchCall(JvmLanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterTypes("int", "long", "double"), """
      class Foo { fun bar(x: Int, y: Long, z: Double) { } }
      
      fun main() { Foo().bar(0, 0L, 0.0) }
    """.trimIndent())
  }

  fun testInstanceMethodReference() {
    checkMatchCallableReference(JvmLanguage.KOTLIN, CallMatcher.instanceCall("java.lang.String", "plus").parameterCount(1), """  
      class Foo { fun bar(arg: String.(String) -> String) { }  }
      
      class Main { fun main() { Foo().bar(String::plus) } }
    """.trimIndent())
  }

  fun testMethodReferenceArgumentTypes() {
    checkMatchCallableReference(JvmLanguage.KOTLIN, CallMatcher.instanceCall("java.lang.String", "plus").parameterTypes("java.lang.Object"), """  
      class Foo { fun bar(arg: String.(String) -> String) { }  }
      
      class Main { fun main() { Foo().bar(String::plus) } }
    """.trimIndent())
  }
}