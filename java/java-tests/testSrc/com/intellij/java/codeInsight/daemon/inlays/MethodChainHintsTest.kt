// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.MethodChainsInlayProvider
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import org.intellij.lang.annotations.Language

class MethodChainHintsTest : InlayHintsProviderTestCase() {
  fun check(@Language("Java") text: String) {
    testProvider("A.java", text, MethodChainsInlayProvider())
  }

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
    new A()
      .b()<# [[temp:///src/A.java:1]Chains . [temp:///src/A.java:99]B] #>
                .c()<# [[temp:///src/A.java:1]Chains . [temp:///src/A.java:173]C] #>
                .a()<# [[temp:///src/A.java:1]Chains . [temp:///src/A.java:25]A] #>
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
    new A()
      .b()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B] #>
      .c().b()
      .a()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A] #>
      .c()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C] #>
      .b()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B] #>
      .c();
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
    a.b().c()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C] #>
     .a()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A] #>
     .b()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B] #>
     .a()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A] #>
     .c()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C] #>
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
     .a()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A] #>
     .b()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B] #>
     .a() // comment
     .c()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C] #>
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
      .b()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B] #>
                .c()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C] #>
                .a()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A] #>
                .c();

    new A()
      .b()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B] #>
                .c()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C] #>
                .a()<# [[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A] #>
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
          .a()<# [[temp:///src/A.java:1]Chains . [temp:///src/A.java:70]A] #>
          .c()<# [[temp:///src/A.java:1]Chains . [temp:///src/A.java:235]C] #>
          .b()<# [[temp:///src/A.java:1]Chains . [temp:///src/A.java:161]B] #>
          .a();
      })<# [[temp:///src/A.java:1]Chains . [temp:///src/A.java:161]B] #>
      .c()<# [[temp:///src/A.java:1]Chains . [temp:///src/A.java:235]C] #>
      .a()<# [[temp:///src/A.java:1]Chains . [temp:///src/A.java:70]A] #>
      .c();
  }
}
""")
  }
}
