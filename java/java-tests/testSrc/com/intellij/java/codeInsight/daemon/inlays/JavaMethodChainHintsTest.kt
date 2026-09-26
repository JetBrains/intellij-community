// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.JavaMethodChainsDeclarativeInlayProvider
import com.intellij.testFramework.utils.inlays.declarative.DeclarativeInlayHintsProviderTestCase
import org.intellij.lang.annotations.Language

class JavaMethodChainHintsTest : DeclarativeInlayHintsProviderTestCase() {
  fun `test plain builder`() {
    check("""

public class Chains {
  static class A {
    B b() {return null;}
    C c() {return null;}
  }

  static class B {
    A a() {return null;}
    C c() {return null;}
  }

  static class C {
    B b() {return null;}
    A a() {return null;}
  }

  @SuppressWarnings("UnusedLabel")
  public static void main(String[] args) {
    new A()
      .b()/*<# [Chains.B:java.fqn.class]B #>*/
                .c()/*<# [Chains.C:java.fqn.class]C #>*/
                .a()/*<# [Chains.A:java.fqn.class]A #>*/
                .c();
  }
}""")
  }

  fun `test duplicated builder`() {
    // IDEA-192777
    check("""public class Chains {
  static class A {
    B b() {return null;}
    C c() {return null;}
  }

  static class B {
    A a() {return null;}
    C c() {return null;}
  }

  static class C {
    B b() {return null;}
    A a() {return null;}
  }

  @SuppressWarnings("UnusedLabel")
  public static void main(String[] args) {
    A a = new A();
    a.b().c() // comment
     .a()/*<# [Chains.A:java.fqn.class]A #>*/
     .b()/*<# [Chains.B:java.fqn.class]B #>*/
     .a() // comment
     .c()/*<# [Chains.C:java.fqn.class]C #>*/
     .b();
  }
}
""")
  }

  fun `test duplicated call with reference qualifier`() {
    check("""public class Chains {
  static class A {
    B b() {return null;}
    C c() {return null;}
  }

  static class B {
    A a() {return null;}
    C c() {return null;}
  }

  static class C {
    B b() {return null;}
    A a() {return null;}
  }

  @SuppressWarnings("UnusedLabel")
  public static void main(String[] args) {
    A a = new A();
    a.b().c()/*<# [Chains.C:java.fqn.class]C #>*/
     .a()/*<# [Chains.A:java.fqn.class]A #>*/
     .b()/*<# [Chains.B:java.fqn.class]B #>*/
     .a()/*<# [Chains.A:java.fqn.class]A #>*/
     .c()/*<# [Chains.C:java.fqn.class]C #>*/
     .b();
  }
}
""")
  }

  fun `test comments`() {
    check("""public class Chains {
  static class A {
    B b() {return null;}
    C c() {return null;}
  }

  static class B {
    A a() {return null;}
    C c() {return null;}
  }

  static class C {
    B b() {return null;}
    A a() {return null;}
  }

  @SuppressWarnings("UnusedLabel")
  public static void main(String[] args) {
    A a = new A();
    a.b().c() // comment
     .a()/*<# [Chains.A:java.fqn.class]A #>*/
     .b()/*<# [Chains.B:java.fqn.class]B #>*/
     .a() // comment
     .c()/*<# [Chains.C:java.fqn.class]C #>*/
     .b();
  }
}
""")
  }

  fun `test several call chains`() {
    check("""public class Chains {
  static class A {
    B b() {return null;}
    C c() {return null;}
  }

  static class B {
    A a() {return null;}
    C c() {return null;}
  }

  static class C {
    B b() {return null;}
    A a() {return null;}
  }

  @SuppressWarnings("UnusedLabel")
  public static void main(String[] args) {
    new A()
      .b()/*<# [Chains.B:java.fqn.class]B #>*/
                .c()/*<# [Chains.C:java.fqn.class]C #>*/
                .a()/*<# [Chains.A:java.fqn.class]A #>*/
                .c();

    new A()
      .b()/*<# [Chains.B:java.fqn.class]B #>*/
                .c()/*<# [Chains.C:java.fqn.class]C #>*/
                .a()/*<# [Chains.A:java.fqn.class]A #>*/
                .c();
  }
}
""")
  }

  fun `test nested call chains`() {
    check("""

public class Chains {
  interface Callable {
    void call();
  }

  static class A {
    B b(Callable callable) {return null;}
    C c() {return null;}
  }

  static class B {
    A a() {return null;}
    C c() {return null;}
  }

  static class C {
    B b() {return null;}
    A a() {return null;}
  }

  @SuppressWarnings("UnusedLabel")
  public static void main(String[] args) {
    new A()
      .b(() -> {
        new B()
          .a()/*<# [Chains.A:java.fqn.class]A #>*/
          .c()/*<# [Chains.C:java.fqn.class]C #>*/
          .b()/*<# [Chains.B:java.fqn.class]B #>*/
          .a();
      })/*<# [Chains.B:java.fqn.class]B #>*/
      .c()/*<# [Chains.C:java.fqn.class]C #>*/
      .a()/*<# [Chains.A:java.fqn.class]A #>*/
      .c();
  }
}
""")
  }

  fun test() {
    check("""
      abstract class Foo<T> {
        void main() {
          Foo
            .getFoo(1)
            .filter(it -> it % 2 == 0)
            .map(it -> it * 2)
            .map(it -> "item: " + it)
            .forEach(this::println);
        }
        
        static <T> Foo<T> getFoo(T t) {
          return null;        
        }

        abstract Void println(Object any);
        abstract Foo<Integer> listOf(int... args);
        abstract Foo<T> filter(Function<T, Boolean> isAccepted);
        abstract <R> Foo<R> map(Function<T, R> mapper);
        abstract void forEach(Function<T, Void> fun);
        interface Function<T, R> {
          R call(T t);
        }
      }""")
  }

  fun check(@Language("Java") text: String) {
    doTestProvider("A.java", text, JavaMethodChainsDeclarativeInlayProvider())
  }

}
