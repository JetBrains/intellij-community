package com.intellij.codeInspection.tests.java

import com.intellij.jvm.analysis.internal.testFramework.ObsoleteApiUsageInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaObsoleteApiUsageInspectionTest : ObsoleteApiUsageInspectionTestBase() {
  fun `test direct usage`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class B {
        void f(A a) {
          a.<warning descr="Obsolete API is used">f</warning>();
        }
      }
    """.trimIndent())
  }

  fun `test override`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      class C extends A {
         void <warning descr="Obsolete API is used">f</warning>() {}
      }
      
      @org.jetbrains.annotations.ApiStatus.Obsolete 
      class D extends A {
         void <warning descr="Obsolete API is used">f</warning>() {}
      }      
    """.trimIndent())
  }

  fun `test generic reference`() {
    myFixture.addClass("@org.jetbrains.annotations.ApiStatus.Obsolete interface I<T> {}")
    myFixture.testHighlighting(JvmLanguage.JAVA, """
class U {
  void u(<warning descr="Obsolete API is used">I</warning><Integer> i) {}
}
""".trimIndent())
  }
  
  fun `test method reference`() {
    myFixture.addClass("import org.jetbrains.annotations.ApiStatus;\n" +
                       "\n" +
                       "@ApiStatus.Obsolete\n" +
                       "@FunctionalInterface\n" +
                       "public interface MyFn {\n" +
                       "\tvoid consumer(int x);\n" +
                       "}\n")
    myFixture.addClass("import org.jetbrains.annotations.ApiStatus;\n" +
                       "\n" +
                       "public class MyClass {\n" +
                       "    @ApiStatus.Obsolete\n" +
                       "    public MyClass(int x) {}\n" +
                       "}")
    myFixture.testHighlighting(JvmLanguage.JAVA, """class Use {
        void test(<warning descr="Obsolete API is used">MyFn</warning> fn) {
          fn.consumer(1);
        }
      
        void use2() {
          test(StringBuilder::new);
          test(new StringBuilder()::append);
          test(MyClass::<warning descr="Obsolete API is used">new</warning>);
          test(x -> new <warning descr="Obsolete API is used">MyClass</warning>(x));
          test(capacity -> new StringBuilder(capacity));
        }
      }""".trimIndent())
  }
}