package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.HamcrestAssertionsConverterInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import java.io.File

class KotlinHamcrestAssertionsConverterInspectionTest : HamcrestAssertionsConverterInspectionTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : JUnitProjectDescriptor(languageLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  fun `test highlighting`() {
    myFixture.testHighlighting(ULanguage.KOTLIN, """
      import org.junit.Assert

      class Foo {
          fun m() {
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 != 3)
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 == 3)
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 > 3)
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 < 3)
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 >= 3)
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 <= 3)
    
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 != 3)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 == 3)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 > 3)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 < 3)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 >= 3)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 <= 3)
          }
  
          fun m2() {
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd".equals("zxc"))
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd" == "zxc")
          }
  
          fun m3(c: Collection<String>, o: String) {
              Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(c.contains(o))
              Assert.<warning descr="Assert expression 'assertEquals' can be replaced with 'assertThat()' call">assertEquals</warning>(c, o)
              Assert.<warning descr="Assert expression 'assertEquals' can be replaced with 'assertThat()' call">assertEquals</warning>("msg", c, o)
              Assert.<warning descr="Assert expression 'assertNotNull' can be replaced with 'assertThat()' call">assertNotNull</warning>(c)
              Assert.<warning descr="Assert expression 'assertNull' can be replaced with 'assertThat()' call">assertNull</warning>(c)
              Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(c.contains(o))
          }
  
          fun m(a: IntArray, b: IntArray) {
              Assert.<warning descr="Assert expression 'assertArrayEquals' can be replaced with 'assertThat()' call">assertArrayEquals</warning>(a, b)
          }
      }      
    """.trimIndent())
  }

  fun `test quickfix binary expression`() {
    myFixture.testAllQuickfixes(ULanguage.KOTLIN, """
      import org.junit.Assert

      class MigrationTest {
          fun migrate() {
              Assert.assertTrue(2 != 3)
              Assert.assertTrue(2 == 3)
              Assert.assertTrue(2 > 3)
              Assert.assertTrue(2 < 3)
              Assert.assertTrue(2 >= 3)
              Assert.assertTrue(2 <= 3)
              Assert.assertFalse(2 != 3)
              Assert.assertFalse(2 == 3)
              Assert.assertFalse(2 > 3)
              Assert.assertFalse(2 < 3)
              Assert.assertFalse(2 >= 3)
              Assert.assertFalse(2 <= 3)
          }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert
      import org.hamcrest.Matchers
      import org.junit.Assert

      class MigrationTest {
          fun migrate() {
              MatcherAssert.assertThat(2, Matchers.not(Matchers.`is`(3)))
              MatcherAssert.assertThat(2, Matchers.`is`(3))
              MatcherAssert.assertThat(2, Matchers.greaterThan(3))
              MatcherAssert.assertThat(2, Matchers.lessThan(3))
              MatcherAssert.assertThat(2, Matchers.greaterThanOrEqualTo(3))
              MatcherAssert.assertThat(2, Matchers.lessThanOrEqualTo(3))
              MatcherAssert.assertThat(2, Matchers.`is`(3))
              MatcherAssert.assertThat(2, Matchers.not(Matchers.`is`(3)))
              MatcherAssert.assertThat(2, Matchers.lessThanOrEqualTo(3))
              MatcherAssert.assertThat(2, Matchers.greaterThanOrEqualTo(3))
              MatcherAssert.assertThat(2, Matchers.lessThan(3))
              MatcherAssert.assertThat(2, Matchers.greaterThan(3))
          }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix string`() {
    myFixture.testAllQuickfixes(ULanguage.KOTLIN, """
      import org.junit.Assert

      class Foo {
          fun migrate() {
              Assert.assertTrue("asd".equals("zxc"))
              Assert.assertTrue("asd" === "zxc")
              Assert.assertTrue("asd".contains("zxc"))
          }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert
      import org.hamcrest.Matchers
      import org.junit.Assert

      class Foo {
          fun migrate() {
              MatcherAssert.assertThat("asd", Matchers.`is`("zxc"))
              MatcherAssert.assertThat("asd", Matchers.sameInstance("zxc"))
              MatcherAssert.assertThat("asd", Matchers.containsString("zxc"))
          }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix collection`() {
    myFixture.testAllQuickfixes(ULanguage.KOTLIN, """
      import org.junit.Assert

      class Foo {
          fun migrate(c: Collection<String>, o: String) {
              Assert.assertTrue(c.contains(o))
              Assert.assertEquals(c, o)
              Assert.assertEquals("msg", c, o)
              Assert.assertNotNull(c)
              Assert.assertNull(c)
              Assert.assertFalse(c.contains(o))
          }
      }      
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert
      import org.hamcrest.Matchers
      import org.junit.Assert

      class Foo {
          fun migrate(c: Collection<String>, o: String) {
              MatcherAssert.assertThat(c, Matchers.hasItem(o))
              MatcherAssert.assertThat(o, Matchers.`is`(c))
              MatcherAssert.assertThat("msg", o, Matchers.`is`(c))
              MatcherAssert.assertThat(c, Matchers.notNullValue())
              MatcherAssert.assertThat(c, Matchers.nullValue())
              MatcherAssert.assertThat(c, Matchers.not(Matchers.hasItem(o)))
          }
      }      
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix array`() {
    myFixture.testAllQuickfixes(ULanguage.KOTLIN, """
      import org.junit.Assert

      class Foo {
          fun migrate(a: IntArray, b: IntArray) {
              Assert.assertArrayEquals(a, b)
          }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert
      import org.hamcrest.Matchers
      import org.junit.Assert

      class Foo {
          fun migrate(a: IntArray, b: IntArray) {
              MatcherAssert.assertThat(b, Matchers.`is`(a))
          }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }
}