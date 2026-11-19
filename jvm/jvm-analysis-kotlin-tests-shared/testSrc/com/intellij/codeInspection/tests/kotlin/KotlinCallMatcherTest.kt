package com.intellij.codeInspection.tests.kotlin

import com.intellij.jvm.analysis.internal.testFramework.callMatcherFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinCallMatcherTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance())
  }

  protected val callMatcher by callMatcherFixture(projectFixture)

  

  @Test
  fun testInstanceMethodCall() {
    callMatcher.checkMatchCall(JvmLanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterCount(0), """
      class Foo { fun bar() { } }
      
      fun main() { Foo().bar() }
    """.trimIndent())
  }

  @Test
  fun testMultipleArgumentsCall() {
    callMatcher.checkMatchCall(JvmLanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterCount(3), """
      class Foo { fun bar(x: Int, y: Int, z: Int) { } }
      
      fun main() { Foo().bar(0, 0, 0) }
    """.trimIndent())
  }

  @Test
  fun testMultipleArgumentsCallDefaultArg() {
    callMatcher.checkMatchCall(JvmLanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterCount(3), """
      class Foo { fun bar(x: Int, y: Int, z: Int = 0) { }  }
      
      fun main() { Foo().bar(0, 0) }
    """.trimIndent())
  }

  @Test
  fun testMultipleArgumentTypes() {
    callMatcher.checkMatchCall(JvmLanguage.KOTLIN, CallMatcher.instanceCall("Foo", "bar").parameterTypes("int", "long", "double"), """
      class Foo { fun bar(x: Int, y: Long, z: Double) { } }
      
      fun main() { Foo().bar(0, 0L, 0.0) }
    """.trimIndent())
  }

  @Test
  fun testInstanceMethodReference() {
    callMatcher.checkMatchCallableReference(JvmLanguage.KOTLIN, CallMatcher.instanceCall("java.lang.String", "plus").parameterCount(1), """  
      class Foo { fun bar(arg: String.(String) -> String) { }  }
      
      class Main { fun main() { Foo().bar(String::plus) } }
    """.trimIndent())
  }

  @Test
  fun testMethodReferenceArgumentTypes() {
    callMatcher.checkMatchCallableReference(JvmLanguage.KOTLIN, CallMatcher.instanceCall("java.lang.String", "plus").parameterTypes("java.lang.Object"), """  
      class Foo { fun bar(arg: String.(String) -> String) { }  }
      
      class Main { fun main() { Foo().bar(String::plus) } }
    """.trimIndent())
  }
}