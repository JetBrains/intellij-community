package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.CallMatcherTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.siyeh.ig.callMatcher.CallMatcher

class JavaCallMatcherTest : CallMatcherTestBase() {
  fun testInstanceMethodCall() {
    checkMatchCall(JvmLanguage.JAVA, CallMatcher.instanceCall("Foo", "bar").parameterCount(0), """
      class Foo { void bar() { } }
      
      class Main { void main() { new Foo().bar(); } }
    """.trimIndent())
  }

  fun testMultipleArgumentsCall() {
    checkMatchCall(JvmLanguage.JAVA, CallMatcher.instanceCall("Foo", "bar").parameterCount(3), """
      class Foo { void bar(int x, int y, int z) { } }
      
      class Main { void main() { new Foo().bar(0, 0, 0); } }
    """.trimIndent())
  }

  fun testMultipleArgumentTypes() {
    checkMatchCall(JvmLanguage.JAVA, CallMatcher.instanceCall("Foo", "bar").parameterTypes("int", "long", "double"), """
      class Foo { void bar(int x, long y, double z) { } }
      
      class Main { void main() { new Foo().bar(0, 0L, 0.0d); } }
    """.trimIndent())
  }

  fun testInstanceMethodReference() {
    checkMatchCallableReference(JvmLanguage.JAVA, CallMatcher.instanceCall("java.lang.String", "concat").parameterCount(1), """
      import java.util.function.BiFunction;
      
      class Foo { void bar(BiFunction<String, String, String> arg) { }  }
      
      class Main { void main() { new Foo().bar(String::concat); } }
    """.trimIndent())
  }

  fun testMethodReferenceArgumentTypes() {
    checkMatchCallableReference(JvmLanguage.JAVA, CallMatcher.instanceCall("java.lang.String", "concat").parameterTypes("java.lang.String"), """  
      import java.util.function.BiFunction;
      
      class Foo { void bar(BiFunction<String, String, String> arg) { }  }
      
      class Main { void main() { new Foo().bar(String::concat); } }
    """.trimIndent())
  }
}