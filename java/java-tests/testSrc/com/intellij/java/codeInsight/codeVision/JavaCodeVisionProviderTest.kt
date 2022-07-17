// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.codeVision

import com.intellij.codeInsight.daemon.impl.JavaInheritorsCodeVisionProvider
import com.intellij.codeInsight.daemon.impl.JavaReferencesCodeVisionProvider

class JavaCodeVisionProviderTest : CodeVisionTestCase() {
  fun testMethodUsages() = doTest("""
class A {
<# block [1 usage] #>
  void foo() {
  }
  
  void bar() {
    foo();
  }
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID)

  fun testClassInheritors() = doTest("""
class A {
<# block [2 usages   2 inheritors] #>
  class B {}
  
  class B1 extends B {}
  class B2 extends B {}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID, JavaInheritorsCodeVisionProvider.ID)

  fun testInterfaceInheritors() = doTest("""
class A {
<# block [1 usage   1 implementation] #>
  interface B {}
  
  class B1 implements B {}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID, JavaInheritorsCodeVisionProvider.ID)

  fun testMethodOverrides() = doTest("""
<# block [1 usage   1 inheritor] #>
class A {
<# block [1 override] #>
  void foo(){}
}
class B extends A {
  void foo(){}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID, JavaInheritorsCodeVisionProvider.ID)

  fun testDefaultMethodOverrides() = doTest("""
<# block [2 usages   2 implementations] #>
interface A {
<# block [2 overrides] #>
  default void foo(){}
}
class B implements A {
  public void foo(){}
}
class B2 implements A {
  public void foo(){}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID, JavaInheritorsCodeVisionProvider.ID)

  fun testAbstractMethodImplementations() = doTest("""
<# block [2 usages   2 implementations] #>
interface A {
<# block [2 implementations] #>
  void foo();
}
class B implements A {
  public void foo(){}
}
class B2 implements A {
  public void foo(){}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID, JavaInheritorsCodeVisionProvider.ID)

  fun testEnumMembers() = doTest("""
class A {
<# block [6 usages] #>
  enum E { 
<# block [1 usage] #>
    E1, E2, E3, E4
  }

  E foo() {
    bar(E.E1, E.E2, E.E3, E.E4);
  }
<# block [1 usage] #>
  void bar(E... e) {}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID)

  fun testClassAtZeroOffset() = doTest("""<# block [1 usage] #>
      |class A{}
      |class B {
      | void use() {
      |   new A();
      | }
      |}
    """.trimMargin(), JavaReferencesCodeVisionProvider.ID)

  private fun doTest(text: String, vararg enabledProviderIds: String) {
    testProviders(text, "test.java", *enabledProviderIds)
  }
}