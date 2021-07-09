// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

class MethodChainHintsTest : LightJavaCodeInsightFixtureTestCase() {
  fun check(@Language("Java") text: String) {
    myFixture.configureByText("A.java", text)
    myFixture.testInlays({ (it.renderer as LinearOrderInlayRenderer<*>).toString() }, { it.renderer is LinearOrderInlayRenderer<*> })
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

  public static void main(String[] args) {
    new A()
    new A()
      .b()<hint text="[[temp:///src/A.java:1]Chains . [temp:///src/A.java:99]B]"/>
                .c()<hint text="[[temp:///src/A.java:1]Chains . [temp:///src/A.java:173]C]"/>
                .a()<hint text="[[temp:///src/A.java:1]Chains . [temp:///src/A.java:25]A]"/>
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

  public static void main(String[] args) {
    new A()
      .b()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B]"/>
      .c().b()
      .a()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A]"/>
      .c()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C]"/>
      .b()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B]"/>
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

  public static void main(String[] args) {
    A a = new A();
    a.b().c()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C]"/>
     .a()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A]"/>
     .b()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B]"/>
     .a()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A]"/>
     .c()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C]"/>
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

  public static void main(String[] args) {
    A a = new A();
    a.b().c() // comment
     .a()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A]"/>
     .b()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B]"/>
     .a() // comment
     .c()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C]"/>
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

  public static void main(String[] args) {
    new A()
      .b()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B]"/>
                .c()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C]"/>
                .a()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A]"/>
                .c();

    new A()
      .b()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:98]B]"/>
                .c()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:172]C]"/>
                .a()<hint text="[[temp:///src/A.java:0]Chains . [temp:///src/A.java:24]A]"/>
                .c();
  }
}
""")
  }

  fun `test nested call chains`() {
    check("""
public class Chains {
  static interface Callable {
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

  public static void main(String[] args) {
    new A()
      .b(() -> {
        new B()
          .a()<hint text="[[temp:///src/A.java:1]Chains . [temp:///src/A.java:77]A]"/>
          .c()<hint text="[[temp:///src/A.java:1]Chains . [temp:///src/A.java:242]C]"/>
          .b()<hint text="[[temp:///src/A.java:1]Chains . [temp:///src/A.java:168]B]"/>
          .a();
      })<hint text="[[temp:///src/A.java:1]Chains . [temp:///src/A.java:168]B]"/>
      .c()<hint text="[[temp:///src/A.java:1]Chains . [temp:///src/A.java:242]C]"/>
      .a()<hint text="[[temp:///src/A.java:1]Chains . [temp:///src/A.java:77]A]"/>
      .c();
  }
}
""")
  }
}
