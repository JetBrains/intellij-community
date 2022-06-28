package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitBeforeAfterClassInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import java.io.File

class KotlinJunitBeforeAfterClassInspectionTest : JUnitBeforeAfterClassInspectionTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(sdkLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  fun `test @BeforeClass non-static highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class MainTest {
        @org.junit.BeforeClass
        fun <warning descr="'beforeClass()' has incorrect signature for a '@org.junit.BeforeClass' method">beforeClass</warning>() { }
      }
    """.trimIndent())
  }

  fun `test @BeforeClass private highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.BeforeClass
          private fun <warning descr="'beforeClass()' has incorrect signature for a '@org.junit.BeforeClass' method">beforeClass</warning>() { }
        }
      }
    """.trimIndent())
  }

  fun `test @BeforeClass parameter highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.BeforeClass
          fun <warning descr="'beforeClass()' has incorrect signature for a '@org.junit.BeforeClass' method">beforeClass</warning>(i: Int) { System.out.println(i) }
        }
      }
    """.trimIndent())
  }

  fun `test @BeforeClass return type highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.BeforeClass
          fun <warning descr="'beforeClass()' has incorrect signature for a '@org.junit.BeforeClass' method">beforeClass</warning>(): String { return "" }
        }
      }
    """.trimIndent())
  }

  fun `test @BeforeClass no highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.BeforeClass
          fun beforeClass() { }
        }
      }
    """.trimIndent())
  }

  fun `test @BeforeAll non-static highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        fun <warning descr="'beforeAll()' has incorrect signature for a '@org.junit.jupiter.api.BeforeAll' method">beforeAll</warning>() { }
      }
    """.trimIndent())
  }

  fun `test @BeforeAll private highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          private fun <warning descr="'beforeAll()' has incorrect signature for a '@org.junit.jupiter.api.BeforeAll' method">beforeAll</warning>() { }
        }
      }
    """.trimIndent())
  }

  fun `test @BeforeAll parameter highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun <warning descr="'beforeAll()' has incorrect signature for a '@org.junit.jupiter.api.BeforeAll' method">beforeAll</warning>(i: Int) { System.out.println(i) }
        }
      }
    """.trimIndent())
  }

  fun `test @BeforeAll return type highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun <warning descr="'beforeAll()' has incorrect signature for a '@org.junit.jupiter.api.BeforeAll' method">beforeAll</warning>(): String { return "" }
        }
      }
    """.trimIndent())
  }

  fun `test @BeforeAll no highlighting test instance per class`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.jupiter.api.TestInstance
      
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        fun beforeAll() { }
      }
    """.trimIndent())
  }

  fun `test @BeforeAll no highlighting static`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun beforeAll() { }
        }
      }
    """.trimIndent())
  }

  fun `test parameter resolver no highlighting`() {
    myFixture.addClass("""
      package com.intellij.test;
      import org.junit.jupiter.api.extension.ParameterResolver;
      public class TestParameterResolver implements ParameterResolver { }
    """.trimIndent())

    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.jupiter.api.extension.*
      import com.intellij.test.TestParameterResolver
      
      @ExtendWith(TestParameterResolver::class)
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun beforeAll(foo: String) { println(foo) }
        }
      }
    """.trimIndent())
  }

  fun `test quickfix change full signature`() {
    myFixture.testQuickFix(ULanguage.KOTLIN, """
      import org.junit.jupiter.api.BeforeAll
      
      class MainTest {
        @BeforeAll
        fun before<caret>All(i: Int): String { return "" }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.BeforeAll
      
      class MainTest {
          companion object {
              @JvmStatic
              @BeforeAll
              fun beforeAll(): Unit {
                  return ""
              }
          }
      }
    """.trimIndent(), "Fix 'beforeAll' method signature")
  }
}