package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.jvm.analysis.internal.testFramework.test.AssertEqualsBetweenInconvertibleTypesInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import java.io.File

abstract class KotlinAssertEqualsBetweenInconvertibleTypesInspectionTest : AssertEqualsBetweenInconvertibleTypesInspectionTestBase(), KotlinPluginModeProvider {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : AssertJProjectDescriptor(LanguageLevel.HIGHEST) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  fun `test AssertJ incompatible types`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.assertj.core.api.Assertions
      import org.assertj.core.api.Assertions.assertThat

      class AssertEqualsBetweenInconvertibleTypes {
        @org.junit.jupiter.api.Test
        fun myTest() {
          assertThat(1).<warning descr="'isSameAs()' between objects of inconvertible types 'int' and 'String'">isSameAs</warning>("foo")
          Assertions.assertThat("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2)
          Assertions.assertThat("foo").isEqualTo("bar") //ok
          assertThat("foo").describedAs("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2)
          Assertions.assertThat("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2)
          Assertions.assertThat(1).<warning descr="'isSameAs()' between objects of inconvertible types 'int' and 'String'">isSameAs</warning>("foo")
          Assertions.assertThat("foo").describedAs("foo").<warning descr="'isSameAs()' between objects of inconvertible types 'String' and 'int'">isSameAs</warning>(2)
          assertThat(IntArray(2)).`as`("array").<warning descr="'isSameAs()' between objects of inconvertible types 'int[]' and 'int'">isSameAs</warning>(2)
          Assertions.assertThat("foo").`as`("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2)
        }
      }
    """.trimIndent())
  }

  // TODO type is not displayed correctly here, something goes wrong with KT to Java type converter
  fun `_test AssertJ single element wrong type`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.assertj.core.api.Assertions
      
      class MyTest {
          @org.junit.jupiter.api.Test
          fun testSingleElement() {
            Assertions.assertThat(listOf(1))
              .singleElement()
              .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1")
          }
      }
    """.trimIndent())
  }

  // TODO type is not displayed correctly here, something goes wrong with KT to Java type converter
  fun `_test AssertJ is equal to null`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.assertj.core.api.Assertions
      
        class MyTest {
            fun myNullable(): MyTest? = null        
        
            @org.junit.jupiter.api.Test
            fun testExtractingNoHighlight() {
                Assertions.assertThat(myNullable()).isEqualTo(null)
            }
          }    
    """.trimIndent())
  }

  // Fails on TC for unknown reason
  fun `_test AssertJ single element extracting type mismatch`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.assertj.core.api.Assertions
    
      class MyTest {
          @org.junit.jupiter.api.Test
          fun testExtractingNoHighlight() {
              Assertions.assertThat(listOf(1))
                .singleElement()
                .extracting(Any::toString)
                .<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(1)
              }
        }   
    """.trimIndent())
  }

  // Fails on TC for unknown reason
  fun `_test AssertJ extracting single element type mismatch`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.assertj.core.api.Assertions
    
      class MyTest {
          @org.junit.jupiter.api.Test
          fun testExtractingNoHighlight() {
              Assertions.assertThat(listOf(1))
                .extracting(Any::toString)
                .singleElement()
                .<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(1)
              }
        }   
    """.trimIndent())
  }

  fun `test AssertJ extracting as method reference type match`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.assertj.core.api.Assertions
    
      class MyTest {
          @org.junit.jupiter.api.Test
          fun testExtractingNoHighlight() {
              Assertions.assertThat(1)
                .describedAs("Mapping to String")
                .extracting(Any::toString)
                .isEqualTo("1")
              }
        }    
    """.trimIndent())
  }

  fun `test AssertJ extracting as lambda type match`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.assertj.core.api.Assertions
      
        class MyTest {
            @org.junit.jupiter.api.Test
            fun testExtractingNoHighlight() {
                Assertions.assertThat(1)
                  .describedAs("Mapping to String")
                  .extracting { value -> "${"$"}value" }
                  .isEqualTo("1")
                }
          }    
    """.trimIndent())
  }
}