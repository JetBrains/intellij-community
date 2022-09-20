// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionTestCase
import com.intellij.codeInsight.daemon.impl.JavaInheritorsCodeVisionProvider
import com.intellij.codeInsight.daemon.impl.JavaReferencesCodeVisionProvider

class JavaCodeVisionProviderTest : CodeVisionTestCase() {
  fun testMethodUsages() = doTest("""
<# block [no usages] #>
class A {
<# block [1 usage] #>
  void foo() {
  }
  
<# block [no usages] #>
  void bar() {
    foo();
  }
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID)

  fun testClassInheritors() = doTest("""
<# block [no usages] #>
class A {
<# block [2 usages   2 inheritors] #>
  class B {}
  
<# block [no usages] #>
  class B1 extends B {}
<# block [no usages] #>
  class B2 extends B {}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID, JavaInheritorsCodeVisionProvider.ID)

  fun testInterfaceInheritors() = doTest("""
<# block [no usages] #>
class A {
<# block [1 usage   1 implementation] #>
  interface B {}
  
<# block [no usages] #>
  class B1 implements B {}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID, JavaInheritorsCodeVisionProvider.ID)

  fun testMethodOverrides() = doTest("""
<# block [1 usage   1 inheritor] #>
class A {
<# block [no usages   1 override] #>
  void foo(){}
}
<# block [no usages] #>
class B extends A {
<# block [no usages] #>
  void foo(){}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID, JavaInheritorsCodeVisionProvider.ID)

  fun testDefaultMethodOverrides() = doTest("""
<# block [2 usages   2 implementations] #>
interface A {
<# block [no usages   2 overrides] #>
  default void foo(){}
}
<# block [no usages] #>
class B implements A {
<# block [no usages] #>
  public void foo(){}
}
<# block [no usages] #>
class B2 implements A {
<# block [no usages] #>
  public void foo(){}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID, JavaInheritorsCodeVisionProvider.ID)

  fun testAbstractMethodImplementations() = doTest("""
<# block [2 usages   2 implementations] #>
interface A {
<# block [no usages   2 implementations] #>
  void foo();
}
<# block [no usages] #>
class B implements A {
<# block [no usages] #>
  public void foo(){}
}
<# block [no usages] #>
class B2 implements A {
<# block [no usages] #>
  public void foo(){}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID, JavaInheritorsCodeVisionProvider.ID)

  fun testEnumMembers() = doTest("""
<# block [no usages] #>
class A {
<# block [6 usages] #>
  enum E { 
<# block [1 usage] #>
    E1, E2, E3, E4
  }

<# block [no usages] #>
  E foo() {
    bar(E.E1, E.E2, E.E3, E.E4);
  }
<# block [1 usage] #>
  void bar(E... e) {}
}
    """.trimIndent(), JavaReferencesCodeVisionProvider.ID)

  fun testClassAtZeroOffset() = doTest("""
<# block [no usages] #>
class A {
<# block [6 usages] #>
  enum E { 
<# block [1 usage] #>
    E1, E2, E3, E4
  }

<# block [no usages] #>
  E foo() {
    bar(E.E1, E.E2, E.E3, E.E4);
  }
<# block [1 usage] #>
  void bar(E... e) {}
}
    """.trimMargin(), JavaReferencesCodeVisionProvider.ID)

  fun testClassAfterPackageStatement() = doTest("""

package com.company;

<# block [1 usage] #>
class A{}
<# block [no usages] #>
class B {
<# block [no usages] #>
 void use() {
   new A();
 }
}
""", JavaReferencesCodeVisionProvider.ID)

  private fun doTest(text: String, vararg enabledProviderIds: String) {
    testProviders(text, "test.java", *enabledProviderIds)
  }
}