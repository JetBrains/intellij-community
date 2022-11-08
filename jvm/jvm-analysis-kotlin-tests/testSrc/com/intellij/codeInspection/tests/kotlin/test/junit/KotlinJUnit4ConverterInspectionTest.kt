// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnit4ConverterInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import java.io.File

class KotlinJUnit4ConverterInspectionTest : JUnit4ConverterInspectionTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : JUnitProjectDescriptor(sdkLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  fun `test highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import junit.framework.TestCase
      
      class <warning descr="'JUnit3Test' could be converted to JUnit4 test case">JUnit3Test</warning> : TestCase() {
        fun testAddition() {
          assertEquals(2, 1 + 1)
        }
      }""".trimIndent())
  }

  fun `test quickfix lifecycle method name conflict`() {
    myFixture.addFileToProject("AbstractJUnit3Test.kt", """
        import junit.framework.TestCase
      
        public abstract class AbstractJUnit3Test : TestCase() {
            override fun setUp() {
                println("setup 2")
                super.setUp()
            }

            override fun tearDown() {
                try {
                    println("tearDown 2")
                } finally {
                    super.tearDown()
                }
            }
        }      
    """.trimIndent())
    myFixture.configureByText("JUnit3Test.kt", """
        import junit.framework.TestCase
      
        class JUnit3<caret>Test : AbstractJUnit3Test() {
            override fun setUp() {
                println("setup 1")
                super.setUp()
            }
  
            fun testAddition() {
                assertEquals(2, 1 + 1)
            }
  
            override fun tearDown() {
                try {
                    println("tearDown 1")
                } finally {
                    super.tearDown()
                }
            }
        }
    """.trimIndent())
    try {
      myFixture.runQuickFix("Convert to JUnit 4 test case")
      fail("Expected ConflictsInTestsException exception te be thrown.")
    } catch(e: BaseRefactoringProcessor.ConflictsInTestsException) {
      assertEquals(e.messages.size, 2)
      assertContainsElements(
        e.messages,
        "Method setUp will have a name collision with its super method",
        "Method tearDown will have a name collision with its super method"
      )
    }
  }

  fun `test quickfix semantic change`() {
    myFixture.testQuickFixException<BaseRefactoringProcessor.ConflictsInTestsException>(ULanguage.KOTLIN, """
        import junit.framework.TestCase
      
        class JUnit3<caret>Test : TestCase() {
            fun testAddition() {
                println(toString())
            }
        }  
    """.trimIndent(), "Convert to JUnit 4 test case") { e ->
      assertEquals(e.messages.size, 1)
      assertContainsElements(
        e.messages,
        "Method call toString() may change semantics when class JUnit3Test is converted to JUnit 4"
      )
    }
  }

  fun `test quickfix removed method`() {
    myFixture.testQuickFixException<BaseRefactoringProcessor.ConflictsInTestsException>(ULanguage.KOTLIN, """
        import junit.framework.TestCase
      
        class JUnit3<caret>Test : TestCase() {
            fun testAddition() {
                println(countTestCases())
            }
        }      
    """.trimIndent(), "Convert to JUnit 4 test case") { e ->
      assertEquals(e.messages.size, 1)
      assertContainsElements(
        e.messages,
        "Method call countTestCases() will not compile when class JUnit3Test is converted to JUnit 4"
      )
    }
  }

  fun `test quickfix non convertable suite`() {
    myFixture.testQuickFixException<BaseRefactoringProcessor.ConflictsInTestsException>(ULanguage.KOTLIN, """
        import junit.framework.TestCase
        import junit.framework.Test
        
        class JUnit3<caret>Test : TestCase() {
            companion object {
              @JvmStatic
              fun suite(): Test {
                println("Creating test suite")
                val suite = TestSuite()
                suite.addTestSuite(Foo::class.java)
                suite.addTestSuite(Bar::class.java)
                return suite
              }
            }

        }
    """.trimIndent(), "Convert to JUnit 4 test case") { e ->
      assertEquals(e.messages.size, 1)
      assertContainsElements(
        e.messages,
        "Migration of suite method for class JUnit3Test has side effects which will be deleted"
      )
    }
  }

  fun `test quickfix class expression suite converter`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      import junit.framework.TestCase
      import junit.framework.TestSuite
      import junit.framework.Test

      class Foo : TestCase() { }
      class Bar : TestCase() { }
      
      class JUnit3<caret>Test : TestCase() {
        companion object {
          @JvmStatic
          fun suite(): Test {
            val suite = TestSuite()
            suite.addTestSuite(Foo::class.java)
            suite.addTestSuite(Bar::class.java)
            return suite
          }          
        }
      }
      """.trimIndent(), """
      import junit.framework.TestCase
      import junit.framework.TestSuite
      import junit.framework.Test
      import org.junit.runner.RunWith
      import org.junit.runners.Suite
      
      class Foo : TestCase() { }
      class Bar : TestCase() { }

      @RunWith(Suite::class)
      @Suite.SuiteClasses(Foo::class, Bar::class)
      class JUnit3Test {
        companion object {
        }
      }
      """.trimIndent(), "Convert to JUnit 4 test case", true)
  }

  fun `test quickfix nested suite converter`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      import junit.framework.TestCase
      import junit.framework.TestSuite
      import junit.framework.Test

      class Foo : TestCase() {
        companion object {
          @JvmStatic
          fun suite(): Test {
            val suite = TestSuite()
            suite.addTestSuite(Bar::class.java)
            return suite
          }
        }
      }
      
      class Bar : TestCase() { }
      
      class JUnit3<caret>Test : TestCase() {
        companion object {
          @JvmStatic
          fun suite(): Test {
            val suite = TestSuite()
            suite.addTest(Foo.suite())
            return suite
          }
        }
      }
      """.trimIndent(), """
      import junit.framework.TestCase
      import junit.framework.TestSuite
      import junit.framework.Test
      import org.junit.runner.RunWith
      import org.junit.runners.Suite
      
      class Foo : TestCase() {
        companion object {
          @JvmStatic
          fun suite(): Test {
            val suite = TestSuite()
            suite.addTestSuite(Bar::class.java)
            return suite
          }
        }
      }
      
      class Bar : TestCase() { }

      @RunWith(Suite::class)
      @Suite.SuiteClasses(Foo.Companion::class)
      class JUnit3Test {
        companion object {
        }
      }
      """.trimIndent(), "Convert to JUnit 4 test case", true)
  }

  fun `test quickfix assertion converter`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      import junit.framework.TestCase
      
      class JUnit3<caret>Test : TestCase {
          fun testAddition() {
              assertEquals(2, 1 + 1)
          }
      }
      """.trimIndent(), """
      import junit.framework.TestCase
      import org.junit.Assert
      import org.junit.Test

      class JUnit3Test {
          @Test
          fun testAddition() {
              Assert.assertEquals(2, 1 + 1)
          }
      }
      """.trimIndent(), "Convert to JUnit 4 test case", true)
  }

  fun `test quickfix setup and teardown converter`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      import junit.framework.TestCase
      
      class JUnit3<caret>Test : TestCase {
          override fun setUp() {
              println("setup")
              super.setUp()
          }

          fun testAddition() {
              assertEquals(2, 1 + 1)
          }

          override fun tearDown() {
              try {
                  println("tearDown")
              } finally {
                  super.tearDown()
              }
          }
      }
      """.trimIndent(), """
      import junit.framework.TestCase
      import org.junit.After
      import org.junit.Assert
      import org.junit.Before
      import org.junit.Test

      class JUnit3Test {
          @Before
          fun setUp() {
              println("setup")
          }

          @Test
          fun testAddition() {
              Assert.assertEquals(2, 1 + 1)
          }

          @After
          fun tearDown() {
              println("tearDown")
          }
      }
      """.trimIndent(), "Convert to JUnit 4 test case", true)
  }
}