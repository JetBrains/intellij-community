// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

class MethodChainHintsTest: LightJavaCodeInsightFixtureTestCase() {
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
      .b()<hint text="[Chains . B]"/>
                .c()<hint text="[Chains . C]"/>
                .a()<hint text="[Chains . A]"/>
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
      .b()<hint text="[Chains . B]"/>
      .c().b()
      .a()<hint text="[Chains . A]"/>
      .c()<hint text="[Chains . C]"/>
      .b()<hint text="[Chains . B]"/>
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
    a.b().c()<hint text="[Chains . C]"/>
     .a()<hint text="[Chains . A]"/>
     .b()<hint text="[Chains . B]"/>
     .a()<hint text="[Chains . A]"/>
     .c()<hint text="[Chains . C]"/>
     .b();
  }
}
""")
  }
}