package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.callMatcherFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.siyeh.ig.callMatcher.CallMatcher
import org.junit.jupiter.api.Test

@TestApplication
class JavaCallMatcherTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val callMatcher by callMatcherFixture(projectFixture)

  @Test
  fun testInstanceMethodCall() {
    callMatcher.checkMatchCall(JvmLanguage.JAVA, CallMatcher.instanceCall("Foo", "bar").parameterCount(0), """
      class Foo { void bar() { } }
      
      class Main { void main() { new Foo().bar(); } }
    """.trimIndent())
  }

  @Test
  fun testMultipleArgumentsCall() {
    callMatcher.checkMatchCall(JvmLanguage.JAVA, CallMatcher.instanceCall("Foo", "bar").parameterCount(3), """
      class Foo { void bar(int x, int y, int z) { } }
      
      class Main { void main() { new Foo().bar(0, 0, 0); } }
    """.trimIndent())
  }

  @Test
  fun testMultipleArgumentTypes() {
    callMatcher.checkMatchCall(JvmLanguage.JAVA, CallMatcher.instanceCall("Foo", "bar").parameterTypes("int", "long", "double"), """
      class Foo { void bar(int x, long y, double z) { } }
      
      class Main { void main() { new Foo().bar(0, 0L, 0.0d); } }
    """.trimIndent())
  }

  @Test
  fun testInstanceMethodReference() {
    callMatcher.checkMatchCallableReference(JvmLanguage.JAVA, CallMatcher.instanceCall("java.lang.String", "concat").parameterCount(1), """
      import java.util.function.BiFunction;
      
      class Foo { void bar(BiFunction<String, String, String> arg) { }  }
      
      class Main { void main() { new Foo().bar(String::concat); } }
    """.trimIndent())
  }

  @Test
  fun testMethodReferenceArgumentTypes() {
    callMatcher.checkMatchCallableReference(JvmLanguage.JAVA, CallMatcher.instanceCall("java.lang.String", "concat").parameterTypes("java.lang.String"), """  
      import java.util.function.BiFunction;
      
      class Foo { void bar(BiFunction<String, String, String> arg) { }  }
      
      class Main { void main() { new Foo().bar(String::concat); } }
    """.trimIndent())
  }
}