package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.test.AssertEqualsBetweenInconvertibleTypesInspectionTestBase

class KotlinAssertEqualsBetweenInconvertibleTypesTest : AssertEqualsBetweenInconvertibleTypesInspectionTestBase() {
  fun `test AssertJ incompatible types`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.assertj.core.api.Assertions
      import org.assertj.core.api.Assertions.assertThat
      import org.junit.jupiter.api.Test

      class AssertEqualsBetweenInconvertibleTypes {
        @Test
        fun myTest() {
          assertThat(1).<warning descr="'isSameAs()' between objects of inconvertible types 'String' and 'int'">isSameAs</warning>("foo")
          Assertions.assertThat("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'int' and 'String'">isEqualTo</warning>(2);
          Assertions.assertThat("foo").isEqualTo("bar"); //ok
          assertThat("foo").describedAs("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'int' and 'String'">isEqualTo</warning>(2);
          Assertions.assertThat("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'int' and 'String'">isEqualTo</warning>(2);
          Assertions.assertThat(1).<warning descr="'isSameAs()' between objects of inconvertible types 'String' and 'int'">isSameAs</warning>("foo")
          Assertions.assertThat("foo").describedAs("foo").<warning descr="'isSameAs()' between objects of inconvertible types 'int' and 'String'">isSameAs</warning>(2)
          assertThat(IntArray(2)).`as`("array").<warning descr="'isSameAs()' between objects of inconvertible types 'int' and 'int[]'">isSameAs</warning>(2)
          Assertions.assertThat("foo").`as`("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'int' and 'String'">isEqualTo</warning>(2);
        }
      }
    """.trimIndent())
  }
}