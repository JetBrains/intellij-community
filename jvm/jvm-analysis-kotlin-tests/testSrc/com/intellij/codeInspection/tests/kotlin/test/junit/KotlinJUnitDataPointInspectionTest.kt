package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitDatapointInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import java.io.File

class KotlinJUnitDataPointInspectionTest : JUnitDatapointInspectionTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : ProjectDescriptor(sdkLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  fun `test @DataPoint no highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class Test {
        companion object {
          @JvmField
          @org.junit.experimental.theories.DataPoint
          val f1: Any? = null
        }
      }
    """.trimIndent())
  }

  fun `test @DataPoint non-static highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class Test {
         @JvmField
         @org.junit.experimental.theories.DataPoint
         val <warning descr="Fields annotated with 'org.junit.experimental.theories.DataPoint' should be 'static'">f1</warning>: Any? = null
      }
    """.trimIndent())
  }

  fun `test @DataPoint non-public highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class Test {
        companion object {
          @JvmStatic
          @org.junit.experimental.theories.DataPoint
          private val <warning descr="Fields annotated with 'org.junit.experimental.theories.DataPoint' should be 'public'">f1</warning>: Any? = null
        }
      }
    """.trimIndent())
  }

  fun `test @DataPoint field highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class Test {
         @org.junit.experimental.theories.DataPoint
         private val <warning descr="Fields annotated with 'org.junit.experimental.theories.DataPoint' should be 'public' and 'static'">f1</warning>: Any? = null
      }
    """.trimIndent())
  }

  fun `test @DataPoint method highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class Test {
         @org.junit.experimental.theories.DataPoint
         private fun <warning descr="Methods annotated with 'org.junit.experimental.theories.DataPoint' should be 'public' and 'static'">f1</warning>(): Any? = null
      }
    """.trimIndent())
  }

  fun `test @DataPoints method highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      class Test {
         @org.junit.experimental.theories.DataPoints
         private fun <warning descr="Methods annotated with 'org.junit.experimental.theories.DataPoints' should be 'public' and 'static'">f1</warning>(): Any? = null
      }
    """.trimIndent())
  }
}