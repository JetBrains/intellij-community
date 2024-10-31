package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.jvm.analysis.internal.testFramework.test.AssertEqualsBetweenInconvertibleTypesInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@RunWith(BlockJUnit4ClassRunner::class)
abstract class KotlinAssertEqualsBetweenInconvertibleTypesInspectionTest : AssertEqualsBetweenInconvertibleTypesInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
    ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
  }

  @Test
  fun `test JUnit 4 assertEquals`() {
    @Language("kotlin") val code = """
      import org.junit.Test
      import kotlin.test.assertEquals
      import java.util.TreeSet
      import java.util.HashSet
      import java.util.ArrayList
        
      interface A
        
      interface B : A
        
      class GenericClass<T>
        
      class AssertEqualsBetweenInconvertibleTypesTest {
        companion object {
          fun areEqual(a: Any, b: GenericClass<String>): Boolean = a == b
        }
        
        @Test
        fun test() {
          val d = 1.0
          assertEquals(1.0, d, 0.0) // fine
        }
        
        @Test
        fun testFoo() {
          org.junit.Assert.<warning descr="'assertEquals()' between objects of inconvertible types 'String' and 'double'">assertEquals</warning>("hello", 1.0)
        }
        
        @Test
        fun testCollection() {
          val c1: Collection<A>? = null
          val c2: Collection<B>? = null
          assertEquals(c1, c2)
          assertEquals(object : ArrayList<String>() {}, ArrayList<String>())
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `JUnit 5 assertEquals`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.junit.jupiter.api.Assertions.assertEquals

      class SampleTest {
        @Test
        fun myTest() {
          <warning descr="'assertEquals()' between objects of inconvertible types 'int' and 'String'">assertEquals</warning>(1, "", "error message")
          <warning descr="'assertEquals()' between objects of inconvertible types 'int' and 'String'">assertEquals</warning>(1, "", { "error message in supplier" })
          assertEquals(1, 42, "message")
          assertEquals(1, 42, { "message" })
        }
      }
    """.trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ assertEquals`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class SampleTest {
        @Test
        fun myTest() {
          Assertions.assertThat("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2)
          Assertions.assertThat("foo").isEqualTo("bar")
          Assertions.assertThat("foo").describedAs("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2)
          Assertions.assertThat("foo").`as`("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `JUnit 4 assertNotEquals`() {
    @Language("kotlin") val code = """
      import org.junit.Test
      import org.junit.Assert.assertNotEquals
      
      class SampleTest {
        @Test
         fun myTest() {
           <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'String' and 'int'">assertNotEquals</weak_warning>("hello", 1)
           <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotEquals</weak_warning>(intArrayOf(0), 1.0)
           assertNotEquals(intArrayOf(0), intArrayOf(1)) // ok
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  fun `test JUnit 5 assertNotEquals`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.junit.jupiter.api.Assertions.assertNotEquals
      
      class SampleTest {
        @Test
        fun myTest() {
          <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'String' and 'int'">assertNotEquals</weak_warning>("hello", 1, "message")
          <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotEquals</weak_warning>(intArrayOf(0), 1.0, "message")
          assertNotEquals(intArrayOf(0), intArrayOf(1)) // ok
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  fun `test AssertJ assertNotEquals`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyTest {
        @Test
        fun myTest() {
          Assertions.assertThat("hello").`as`("test").<weak_warning descr="Possibly redundant assertion: incompatible types are compared 'String' and 'int'">isNotEqualTo</weak_warning>(1)
          Assertions.assertThat(intArrayOf(0)).describedAs("test").<weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int[]' and 'double'">isNotEqualTo</weak_warning>(1.0)
          Assertions.assertThat(intArrayOf(0)).isNotEqualTo(intArrayOf(1)) //ok
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  fun `test JUnit 4 assertNotSame`() {
    @Language("kotlin") val code = """
      import org.junit.Test
      import org.junit.Assert.assertNotSame
      
      class MyTest {
        @Test
        fun myTest() {
          <warning descr="Redundant assertion: incompatible types are compared 'String' and 'int'">assertNotSame</warning>("hello", 1)
          <warning descr="Redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotSame</warning>(intArrayOf(0), 1.0)
          assertNotSame(intArrayOf(0), intArrayOf(1)) // ok
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `JUnit 5 assertNotSame`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.junit.jupiter.api.Assertions.assertNotSame
      
      class MyTest {
        @Test
        fun myTest() {
          <warning descr="Redundant assertion: incompatible types are compared 'String' and 'int'">assertNotSame</warning>("hello", 1, "message")
          <warning descr="Redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotSame</warning>(intArrayOf(0), 1.0, "message")
          assertNotSame(intArrayOf(0), intArrayOf(1)) // ok
        }
      }
    """.trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ assertNotSame`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions.assertThat
      
      class MyTest {
        @Test
        fun myTest() {
          assertThat("hello").`as`("test").<warning descr="Redundant assertion: incompatible types are compared 'String' and 'int'">isNotSameAs</warning>(1)
          assertThat(intArrayOf(0)).describedAs("test").<warning descr="Redundant assertion: incompatible types are compared 'int[]' and 'double'">isNotSameAs</warning>(1.0)
          assertThat(intArrayOf(0)).isNotSameAs(intArrayOf(1)) // ok
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `JUnit 4 assertSame`() {
    @Language("kotlin") val code = """
      import org.junit.Test
      import org.junit.Assert.assertSame
      
      class MyTest {
        @Test
        fun myTest() {      
          <warning descr="'assertSame()' between objects of inconvertible types 'String' and 'int'">assertSame</warning>("foo", 2)
          <warning descr="'assertSame()' between objects of inconvertible types 'int[]' and 'int'">assertSame</warning>(intArrayOf(2), 2)
          assertSame(1, 2) // ok
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `JUnit 5 assertSame`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.junit.jupiter.api.Assertions.assertSame
      
      class MyTest {
        @Test
        fun myTest() {
          <warning descr="'assertSame()' between objects of inconvertible types 'int' and 'String'">assertSame</warning>(1, "", "Foo")
          <warning descr="'assertSame()' between objects of inconvertible types 'int' and 'int[]'">assertSame</warning>(1, intArrayOf(2), { "Foo in supplier"})
          assertSame(1, 2, "message") // ok
          assertSame(1, 2, { "message" }) // ok
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ assertSame`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions.assertThat
      
      class MyTest {
        @Test
        fun myTest() {
          assertThat(1).<warning descr="'isSameAs()' between objects of inconvertible types 'int' and 'String'">isSameAs</warning>("foo")
          assertThat("foo").describedAs("foo").<warning descr="'isSameAs()' between objects of inconvertible types 'String' and 'int'">isSameAs</warning>(2)
          assertThat(intArrayOf(2)).`as`("array").<warning descr="'isSameAs()' between objects of inconvertible types 'int[]' and 'int'">isSameAs</warning>(2)
          assertThat(1).isSameAs(2) // ok
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ first element type match`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MySampleTest {
          @Test
          fun testNoHighlightBecauseCorrect() {
              Assertions.assertThat(listOf("1"))
                .first()
                .isEqualTo("1")
          }
          
          @Test
          fun testHighlightBecauseIncorrect() {
              Assertions.assertThat(listOf("1"))
                .first()
                .<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(1)
          }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ last element type match`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyList<T> : ArrayList<Int>()
      
      class MySampleTest {
        @Test
        fun testNoHighlightBecauseCorrect() {
          Assertions.assertThat(listOf("1"))
            .last()
            .isEqualTo("1")
        }
       
        @Test
        fun testNoHighlightBecauseCorrect_complexCase() {
          val myList = MyList<String>()
          myList.add(1)
          Assertions.assertThat(myList)
            .last()
            .isEqualTo(1)
        }
       
        @Test
        fun testHighlightBecauseIncorrect_complexCase() {
          val myList = MyList<String>()
          myList.add(1)
          Assertions.assertThat(myList)
            .last()
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1")
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ single element type match`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyTest {
        @Test
        fun testSingleElement() {
          Assertions.assertThat(listOf(1))
            .singleElement()
            .isEqualTo(1)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ single element type mismatch`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyTest {
        @Test
        fun testSingleElement() {
          Assertions.assertThat(listOf(1))
            .singleElement()
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1")
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ element type match`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyTest {
        @Test
        fun testSingleElement() {
          Assertions.assertThat(listOf(1, 2, 3))
            .element(1)
            .isEqualTo(2)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ element type mismatch`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyTest {
        @Test
        fun testSingleElement() {
          Assertions.assertThat(listOf(1, 2, 3))
            .element(1)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("2")
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ extracting single element lambda type match`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      import kotlin.random.Random
      
      class MySampleTest {
        @Test
        fun testExtractingHighlightBecauseIncorrect() {
          Assertions.assertThat(listOf("1"))
            .extracting<Int>({ elem -> elem.toInt() })
            .singleElement()
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1")
        }
      
        @Test
        fun testExtractingNoHighlightBecauseCorrect() {
          Assertions.assertThat(listOf("1"))
            .extracting<Int>({ elem -> elem.toInt() })
            .singleElement()
            .isEqualTo(1)
        }
      
        @Test
        fun testExtractingNoHighlightBecauseTooComplex() {
          Assertions.assertThat(listOf("1"))
            .extracting<Any>( { value ->
              if (Random.nextDouble() > 0.5) {
                value.toString()
              } else {
                value
              }
            })
            .singleElement()
            .isEqualTo("1")
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ extracting single element method reference type match`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      import kotlin.random.Random
      
      class MySampleTest {
        @Test
        fun testExtractingHighlightBecauseIncorrect() {
        Assertions.assertThat(listOf("1"))
          .extracting<Int>(String::toInt)
          .singleElement()
          .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1")
        }
      
        @Test
        fun testExtractingNoHighlightBecauseCorrect() {
          Assertions.assertThat(listOf("1"))
            .extracting<Int>(String::toInt)
            .singleElement()
            .isEqualTo(1)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ extracting single element method reference type match - complex case`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      import kotlin.random.Random
      
      class MySampleTest {
        @Test
        fun testExtractingHighlightBecauseIncorrect() {
          val giveMeFunction: () -> ((String) -> Int) = { { value: String -> value.toInt() } }
          Assertions.assertThat(listOf("1"))
            .extracting<Int>(giveMeFunction())
            .singleElement()
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1")
        }
            
        @Test
        fun testExtractingNoHighlightBecauseCorrect() {
          val giveMeFunction: () -> ((String) -> Int) = { { value: String -> value.toInt() } }
          Assertions.assertThat(listOf("1"))
            .extracting<Int>(giveMeFunction())
            .singleElement()
            .isEqualTo(1)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }


  @Test
  fun `AssertJ incompatible types`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      import org.assertj.core.api.Assertions.assertThat
      
      class AssertEqualsBetweenInconvertibleTypes {
        @Test
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
    """.trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ single element wrong type`() {
    @Language("kotlin") val code = """ 
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyTest {
        @Test
        fun testSingleElement() {
          Assertions.assertThat(listOf(1))
            .singleElement()
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1")
            // Type here is "? extends ActualType>" while in Java it's just "ActualType". Something goes wrong with KT to Java type converter
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ is equal to null`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyTest {
        fun myNullable(): MyTest? = null
      
        @Test
        fun testNoHighlightBecauseCorrect() {
          Assertions.assertThat(myNullable()).isEqualTo(null)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ single element extracting type mismatch`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyTest {
        @Test
        fun testHighlightBecauseIncorrect() {
          Assertions.assertThat(listOf(1))
            .singleElement()
            .extracting(Any::toString)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(1)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ extracting single element type mismatch`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyTest {
        @Test
        fun testExtractingNoHighlight() {
          Assertions.assertThat(listOf(1))
            // type argument has to be specified explicitly because of https://github.com/assertj/assertj/issues/1499 and/or https://github.com/assertj/assertj/issues/2357
            .extracting<String>(Any::toString)
            .singleElement()
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(1)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ extracting method reference type match 1`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
        
      class MyTest {
        @Test
        fun testExtractingNoHighlight() {
          Assertions.assertThat(1)
            .describedAs("Mapping to String")
            .extracting(Any::toString)
            .isEqualTo("1")
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ extracting method reference type match 2`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MySampleTest {
        @Test
        fun testExtractingNoHighlightBecauseCorrect() {
          Assertions.assertThat(1)
            .extracting(Int::toDouble)
            .isEqualTo(1.0)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ extracting method reference type mismatch 1`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MySampleTest {
        @Test
        fun testExtractingHighlightBecauseIncorrect() {
          Assertions.assertThat(1)
            .extracting(Int::toDouble)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Double' and 'String'">isEqualTo</warning>("1")
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ extracting as lambda type match`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MyTest {
        @Test
        fun testExtractingNoHighlight() {
          Assertions.assertThat(1)
            .describedAs("Mapping to String")
            .extracting { value -> "${"$"}value" }
            .isEqualTo("1")
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ assertEquals List and Stream with matching generics`() {
    @Language("kotlin") val code = """
    import org.junit.jupiter.api.Test
    import org.assertj.core.api.Assertions
    import java.util.ArrayList
    import java.util.stream.Stream
    import java.util.stream.IntStream
    import java.util.stream.LongStream
    
    class MyList<T> : ArrayList<String>()
    
    class MySampleTest {
      @Test
      fun testNoHighlightBecauseCorrect() {
        val stream = Stream.of("a", "b", "c")
        val list = listOf("a", "b", "c")
        Assertions
          .assertThat(stream)
          .isEqualTo(list)
      }
      
      @Test
      fun testNoHighlightBecauseCorrect_primitiveInt() {
        val stream = IntStream.of(1, 2, 3)
        Assertions
          .assertThat(stream)
          .isEqualTo(listOf(1, 2, 3))
      }
      
      @Test
      fun testNoHighlightBecauseCorrect_primitiveLong() {
        val stream = LongStream.of(1L, 2L, 3L)
        Assertions
          .assertThat(stream)
          .isEqualTo(listOf(1L, 2L, 3L))
      }
      
      @Test
      fun testNoHighlightBecauseCorrect_complexCase() {
        val stream = Stream.of<CharSequence>("a", "b", "c")
        val list = listOf("a", "b", "c")
        Assertions
          .assertThat(stream)
          .isEqualTo(list)
      }
      
      @Test
      fun testNoHighlightBecauseCorrect_moreComplexCase() {
        val stream = Stream.of<CharSequence>("a", "b", "c")
        val list = MyList<Int>()
        Assertions
          .assertThat(stream)
          .isEqualTo(list)
      }
    }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ assertEquals List and Stream with non-matching generics`() {
    @Language("kotlin") val code = """
    import org.assertj.core.api.Assertions
    import org.junit.jupiter.api.Test
    import java.util.stream.Stream
    import java.util.stream.IntStream
    
    class MySampleTest {
      @Test
      fun testHighlightBecauseIncorrect() {
        val stream = Stream.of("a", "b", "c")
        val list = listOf(1, 2, 3)
        Assertions
          .assertThat(stream)
          .<warning descr="'isEqualTo()' between objects of inconvertible types 'Stream<String>' and 'List<? extends Integer>'">isEqualTo</warning>(list)
      }
      
      @Test
      fun testHighlightBecauseIncorrect_primitives() {
        val stream = IntStream.of(1, 2, 3)
        Assertions
          .assertThat(stream)
          .<warning descr="'isEqualTo()' between objects of inconvertible types 'IntStream' and 'List<? extends String>'">isEqualTo</warning>(listOf("1", "2", "3"))
      }
    }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Ignore("We are aware of this edge case, but do not support it for now. See IDEA-360595")
  @Test
  fun `AssertJ assertEquals List and Stream with non-matching generics (more complex case)`() {
    @Language("kotlin") val code = """
    import org.assertj.core.api.Assertions
    import org.junit.jupiter.api.Test
    import java.util.ArrayList
    import java.util.stream.Stream
    import java.util.stream.IntStream
    
    class MyList<T> : ArrayList<Int>()
    
    class MySampleTest {
      @Test
      fun testHighlightBecauseIncorrect_complexCase() {
        val stream = Stream.of("a", "b", "c")
        val list = MyList<String>()
        Assertions
          .assertThat(stream)
          .<warning descr="'isEqualTo()' between objects of inconvertible types 'Stream<String>' and 'MyList<String>'">isEqualTo</warning>(list)
      }
    }
  """.trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ assertEquals Stream and Stream with matching generics`() {
    @Language("kotlin") val code = """
      import org.assertj.core.api.Assertions
      import org.junit.jupiter.api.Test
      import java.util.stream.Stream
      
      class MySampleTest {
        @Test
        fun myTest_1() {
          val stream1 = Stream.of("a", "b", "c")
          val stream2 = Stream.of("a", "b", "c")
          Assertions
            .assertThat(stream1)
            .isEqualTo(stream2)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ assertEquals Stream and Stream with non-matching generics`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      import java.util.stream.Stream
      
      class MySampleTest {
        @Test
        fun mySampleTest() {
          val stream1 = Stream.of("a", "b", "c")
          val stream2 = Stream.of(1, 2, 3)
          Assertions
            .assertThat(stream1)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Stream<String>' and 'Stream<Integer>'">isEqualTo</warning>(stream2)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `AssertJ using recursive comparison does not warn`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
        
      class RecursiveAssertJIntelliJIssueTest {
        @Test
        fun recursiveComparison() {
          class A { var value: Int = 0 }
          class B { var value: Int = 0 }
         
          val actual = A()
          actual.value = 1
           
          val expected = B()
          expected.value = 1
          
          Assertions.assertThat(actual)
            .usingRecursiveComparison()
            .isEqualTo(expected)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `additional AssertJ smoke test 1`() {
    @Language("kotlin")
    val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      import org.assertj.core.api.InstanceOfAssertFactories
      import java.math.BigInteger

      class MySampleTest {
        @Test
        fun myTest() {
          val sourceList = listOf(1, 2, 3)
          Assertions.assertThat(sourceList)
            .element(1, InstanceOfAssertFactories.DOUBLE)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'double' and 'BigInteger'">isEqualTo</warning>(BigInteger.ONE)
        }
      }
    """.trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `additional AssertJ smoke test 2`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MySampleTest {
        @Test
        fun myTest() {
          val uoe = UnsupportedOperationException()
          Assertions.assertThat(IllegalArgumentException("Hello", uoe))
            .rootCause()
            .isEqualTo(uoe)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `additional AssertJ smoke test 3`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions

      class MySampleTest {
        @Test
        fun myTest() {
          val result = listOf(1, 2, 3).flatMap { listOf(it.toString()) }
          Assertions.assertThat(result)
            .isEqualTo(listOf("1", "2", "3"))
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `additional AssertJ smoke test 4`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      import java.io.File
      import java.io.IOException
      import java.nio.file.Files
      import java.nio.file.Path
      
      class MySampleTest {
        @Test
        fun myTest() {
          val data = byteArrayOf(1, 2, 3)
          Files.write(Path.of("test.dat"), data)
          val file = File("test.dat")
          Assertions.assertThat(file)
            .binaryContent()
            .isEqualTo(data)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `additional AssertJ smoke test 5`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MySampleTest {
        @Test
        fun myTest() {
          val map = mapOf(1 to "a", 2 to "b")
          Assertions.assertThat(map)
            .extractingByKey(1)
            .isEqualTo("a")
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `additional AssertJ smoke test 6`() {
    @Language("kotlin") val code = """
      import org.junit.jupiter.api.Test
      import org.assertj.core.api.Assertions
      
      class MySampleTest {
        @Test
        fun myTest() {
          val map = mapOf(1 to "a", 2 to "b")
          Assertions.assertThat(map)
            .extractingByKey(1)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(1)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  @Test
  fun `Kotlin nullable types`() {
    @Language("kotlin") val code = """
      import org.junit.Test
      import org.junit.Assert
      
      class MySampleTest {
        @Test
        fun myTest() {
          val actual: String = "hello"
          val expected: String? = "hello"
          Assert.assertEquals(expected, actual)
          Assert.assertEquals(actual, expected)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }

  // Smoke test for IDEA-361908
  @Test
  fun `Kotlin Nothing type`() {
    @Language("kotlin") val code = """
      import org.junit.Test
      import org.junit.Assert
      
      class MySampleTest {
        @Test
        fun myTest() {
          val actual: String = "hello"
          val expected: Nothing? = null
          Assert.assertEquals(expected, actual)
          Assert.assertEquals(actual, expected)
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.KOTLIN, code)
  }
}
