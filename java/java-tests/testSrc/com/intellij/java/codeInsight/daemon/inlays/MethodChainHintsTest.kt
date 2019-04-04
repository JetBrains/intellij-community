/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.intellij.lang.annotations.Language

class MethodChainHintsTest: LightCodeInsightFixtureTestCase() {
  fun check(@Language("Java") text: String) {
    myFixture.configureByText("A.java", text)
    myFixture.testInlays({ (it.renderer as HintRenderer).text }, { it.renderer is HintRenderer })
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
      .b()<hint text="B"/>
                .c()<hint text="C"/>
                .a()<hint text="A"/>
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
      .b()<hint text="B"/>
      .c().b()<hint text="B"/>
      .a()<hint text="A"/>
      .c()<hint text="C"/>
      .b()<hint text="B"/>
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
    a.b().c()<hint text="C"/>
     .a()<hint text="A"/>
     .b()<hint text="B"/>
     .a()<hint text="A"/>
     .c()<hint text="C"/>
     .b();
  }
}
""")
  }
}