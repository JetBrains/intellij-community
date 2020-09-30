// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.inlays

import com.intellij.codeInsight.daemon.impl.JavaCodeVisionProvider
import com.intellij.codeInsight.daemon.impl.analysis.JavaCodeVisionSettings
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase

class JavaCodeVisionProviderTest : InlayHintsProviderTestCase() {
  fun testMethodUsages() {
    doTest("""
class A {
<# block [   1 usage  ] #>
  void foo() {
  }
  
  void bar() {
    foo();
  }
}
    """.trimIndent())
  }

  fun testClassInheritors() {
    doTest("""
class A {
<# block [   2 usages   2 inheritors  ] #>
  class B {}
  
  class B1 extends B {}
  class B2 extends B {}
}
    """.trimIndent())
  }
  fun testInterfaceInheritors() {
    doTest("""
class A {
<# block [   1 usage   1 implementation  ] #>
  interface B {}
  
  class B1 implements B {}
}
    """.trimIndent())
  }

  fun testMethodOverrides() {
    doTest("""
class A {
<# block [   1 override  ] #>
  void foo(){}
}
class B extends A {
  void foo(){}
}
    """.trimIndent())
  }

  fun testDefaultMethodOverrides() {
    doTest("""
interface A {
<# block [   2 overrides  ] #>
  default void foo(){}
}
class B implements A {
  public void foo(){}
}
class B2 implements A {
  public void foo(){}
}
    """.trimIndent())
  }

  fun testAbstractMethodImplementations() {
    doTest("""
interface A {
<# block [   2 implementations  ] #>
  void foo();
}
class B implements A {
  public void foo(){}
}
class B2 implements A {
  public void foo(){}
}
    """.trimIndent())
  }

  fun testEnumMembers() {
    doTest("""
class A {
<# block [   6 usages  ] #>
  enum E { 
<# block [     1 usage  ] #>
    E1, E2, E3, E4
  }

  E foo() {
    bar(E.E1, E.E2, E.E3, E.E4);
  }
<# block [   1 usage  ] #>
  void bar(E... e) {}
}
    """.trimIndent())
  }

  private fun doTest(
    text: String,
    settings: JavaCodeVisionSettings = JavaCodeVisionSettings(
      true, true)
  ) {
    testProvider(
      "test.java",
      text,
      JavaCodeVisionProvider(),
      settings
    )
  }
}