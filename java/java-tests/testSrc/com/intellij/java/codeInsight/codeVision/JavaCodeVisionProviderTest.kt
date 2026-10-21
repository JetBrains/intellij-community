// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.codeVision

import com.intellij.codeInsight.daemon.impl.JavaInheritorsCodeVisionProvider
import com.intellij.codeInsight.daemon.impl.JavaReferencesCodeVisionProvider
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.utils.codeVision.CodeVisionTestUtil
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaCodeVisionProviderTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    CodeVisionTestUtil.setUp(myFixture)
  }

  @Test
  fun testMethodUsages(): Unit = doTest("""
    class A { /*<# [no usages] #>*/
    
      void foo() { /*<# [1 usage] #>*/
      }
      
      void bar() { /*<# [no usages] #>*/
        foo();
      }
    }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

  @Test
  fun testClassInheritors(): Unit = doTest("""
      class A { /*<# [no usages] #>*/
      
        class B {} /*<# [2 usages   2 inheritors] #>*/
        
        class B1 extends B {} /*<# [no usages] #>*/
      
        class B2 extends B {} /*<# [no usages] #>*/
      }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)

  @Test
  fun testInterfaceInheritors(): Unit = doTest("""
      class A { /*<# [no usages] #>*/
        interface B {} /*<# [1 usage   1 implementation] #>*/
        
        class B1 implements B {} /*<# [no usages] #>*/
      }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)

  @Test
  fun testMethodOverrides(): Unit = doTest("""
      class A { /*<# [1 usage   1 inheritor] #>*/
        void foo(){} /*<# [no usages   1 override] #>*/
      }
      
      class B extends A { /*<# [no usages] #>*/
        void foo(){} /*<# [no usages] #>*/
      }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)

  @Test
  fun testDefaultMethodOverrides(): Unit = doTest("""
      interface A { /*<# [2 usages   2 implementations] #>*/
        default void foo(){} /*<# [no usages   2 overrides] #>*/
      }
      
      class B implements A { /*<# [no usages] #>*/
        public void foo(){} /*<# [no usages] #>*/
      }
      
      class B2 implements A { /*<# [no usages] #>*/
        public void foo(){} /*<# [no usages] #>*/
      }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)

  @Test
  fun testAbstractMethodImplementations(): Unit = doTest("""
      interface A {/*<# [2 usages   2 implementations] #>*/
        void foo();/*<# [no usages   2 implementations] #>*/
      }
      
      class B implements A { /*<# [no usages] #>*/
        public void foo(){} /*<# [no usages] #>*/
      }
      
      class B2 implements A { /*<# [no usages] #>*/
        public void foo(){} /*<# [no usages] #>*/
      }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId, JavaInheritorsCodeVisionProvider().groupId)

  @Test
  fun testEnumMembers(): Unit = doTest("""
      class A { /*<# [no usages] #>*/
      
        enum E { /*<# [6 usages] #>*/
          E1, E2, E3, E4 /*<# [1 usage] #>*/
        }
      
        E foo() { /*<# [no usages] #>*/
          bar(E.E1, E.E2, E.E3, E.E4);
        }
      
        void bar(E... e) {} /*<# [1 usage] #>*/
      }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

  @Test
  fun testEnumConstructor(): Unit = doTest("""
    enum LocationType {/*<# [no usages] #>*/
        FOREST("Forest"),/*<# [no usages] #>*/
        PLAIN("Plain"),/*<# [no usages] #>*/
        DESERT("Desert"),/*<# [no usages] #>*/
        HILLS("hills");/*<# [no usages] #>*/
    
        private final String typeName;/*<# [2 usages] #>*/
    
        LocationType(String typeName) {/*<# [4 usages] #>*/
            this.typeName = typeName;
        }
    
        public String getTypeName() {/*<# [no usages] #>*/
            return typeName;
        }
    }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

  @Test
  fun testClassAtZeroOffset(): Unit = doTest("""      
      class A { /*<# [no usages] #>*/
        enum E { /*<# [6 usages] #>*/
          E1, E2, E3, E4 /*<# [1 usage] #>*/
        }
      
        E foo() { /*<# [no usages] #>*/
          bar(E.E1, E.E2, E.E3, E.E4);
        }
      
        void bar(E... e) {} /*<# [1 usage] #>*/
      }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

  @Test
  fun testClassAfterPackageStatement(): Unit = doTest("""
      package com.company;
      
      class A{} /*<# [1 usage] #>*/
      
      class B { /*<# [no usages] #>*/
      
       void use() { /*<# [no usages] #>*/
         new A();
       }
      }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

  @Test
  fun testFieldOnFirstLineOfInterfaceHasLenses(): Unit = doTest("""
      package codeLenses;
      
      public interface Interface { /*<# [no usages] #>*/
          String s = "asd"; /*<# [no usages] #>*/
      
          String asd = "asd"; /*<# [no usages] #>*/
      }
    """.trimIndent(), JavaReferencesCodeVisionProvider().groupId)

  private fun doTest(@Language("JAVA") text: String, vararg enabledProviderGroupIds: String) {
    CodeVisionTestUtil.testProviders(myFixture, text, "test.java", *enabledProviderGroupIds)
  }
}