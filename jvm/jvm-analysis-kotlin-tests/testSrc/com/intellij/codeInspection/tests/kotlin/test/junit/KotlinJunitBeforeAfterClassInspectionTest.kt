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

  fun testHighlighting() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.jupiter.api.BeforeAll
      
      class MainTest {
        @BeforeAll
        fun <warning descr="'beforeAll()' has incorrect signature for a '@org.junit.jupiter.api.BeforeAll' method">beforeAll</warning>(i: Int): String { return "${'$'}i" }
      }
    """.trimIndent())
  }

  fun testNoHighlighting() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.jupiter.api.BeforeAll
      
      class MainTest {
        companion object {
          @JvmStatic
          @BeforeAll
          fun beforeAll() { }
        }
      }
    """.trimIndent())
  }

  fun testQuickFixFull() {
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