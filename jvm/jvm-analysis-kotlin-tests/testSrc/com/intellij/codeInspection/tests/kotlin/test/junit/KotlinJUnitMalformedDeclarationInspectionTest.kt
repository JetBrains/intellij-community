package com.intellij.codeInspection.tests.kotlin.test.junit

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.test.junit.JUnitMalformedDeclarationInspectionTestBase
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import java.io.File

class KotlinJUnitMalformedDeclarationInspectionTest : JUnitMalformedDeclarationInspectionTestBase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = object : JUnitProjectDescriptor(sdkLevel) {
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      val jar = File(PathUtil.getJarPathForClass(JvmStatic::class.java))
      PsiTestUtil.addLibrary(model, "kotlin-stdlib", jar.parent, jar.name)
    }
  }

  /* Malformed extensions */
  fun `test malformed extension no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class A {
        @JvmField
        @org.junit.jupiter.api.extension.RegisterExtension
        val myRule5 = Rule5()
        class Rule5 : org.junit.jupiter.api.extension.Extension { }
      }
    """.trimIndent())
  }
  fun `test malformed extension subtype highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class A {
        @JvmField
        @org.junit.jupiter.api.extension.RegisterExtension
        val <warning descr="Field 'myRule5' annotated with '@RegisterExtension' should be of type 'org.junit.jupiter.api.extension.Extension'">myRule5</warning> = Rule5()
        class Rule5 { }
      }
    """.trimIndent())
  }
  fun `test malformed extension make public quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        val myRule<caret>5 = Rule5()
        class Rule5 { }
      }
    """.trimIndent(), """
      class A {
        @JvmField
        @org.junit.jupiter.api.extension.RegisterExtension
        val myRule5 = Rule5()
        class Rule5 { }
      }
    """.trimIndent(), "Fix 'myRule5' field signature")
  }

  /* Malformed nested class */
  fun `test malformed nested no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class A {
        @org.junit.jupiter.api.Nested
        inner class B { }
      }
    """.trimIndent())
  }
  fun `test malformed nested class highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class A {
        @org.junit.jupiter.api.Nested
        class <warning descr="Class 'B' annotated with '@Nested' should be non-static">B</warning> { }
      }
    """.trimIndent())
  }
  fun `test malformed nested class quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
        class A {
            @org.junit.jupiter.api.Nested
            class B<caret> { }
        }
    """.trimIndent(), """
        class A {
            @org.junit.jupiter.api.Nested
            inner class B { }
        }
    """.trimIndent(), "Fix 'B' class signature")
  }

  /* Malformed parameterized */
  fun `test malformed parameterized no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      enum class TestEnum { FIRST, SECOND, THIRD }
      
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(ints = [1])
        fun testWithIntValues(i: Int) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(longs = [1L])
        fun testWithIntValues(i: Long) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(doubles = [0.5])
        fun testWithDoubleValues(d: Double) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = [""])
        fun testWithStringValues(s: String) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = ["foo"])
        fun implicitParameter(argument: String, testReporter: org.junit.jupiter.api.TestInfo) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = ["FIRST"])
        fun implicitConversionEnum(e: TestEnum) { }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = ["1"])
        fun implicitConversionString(i: Int) { }
          
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = ["title"])
        fun implicitConversionClass(book: Book) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = ["6.7.1", "7.3.3", "7.5.1"])
        fun test(gradleVersion: String, @org.junit.jupiter.api.io.TempDir projectDir: java.io.File) { }

        class Book(val title: String) { }
      }
      
      class MethodSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("stream")
        fun simpleStream(x: Int, y: Int) { System.out.println("${'$'}x, ${'$'}y") }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("iterable")
        fun simpleIterable(x: Int, y: Int) { System.out.println("${'$'}x, ${'$'}y") }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = ["stream", "iterator", "iterable"])
        fun parametersArray(x: Int, y: Int) { System.out.println("${'$'}x, ${'$'}y") }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = ["stream", "iterator"])
        fun implicitValueArray(x: Int, y: Int) { System.out.println("${'$'}x, ${'$'}y") }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = ["argumentsArrayProvider"])
        fun argumentsArray(x: Int, s: String) { System.out.println("${'$'}x, ${'$'}s") }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = ["anyArrayProvider"])
        fun anyArray(x: Int, s: String) { System.out.println("${'$'}x, ${'$'}s") }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = ["any2DArrayProvider"])
        fun any2DArray(x: Int, s: String) { System.out.println("${'$'}x, ${'$'}s") }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("intStreamProvider")
        fun intStream(x: Int) { System.out.println(x) }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("intStreamProvider")
        fun injectTestReporter(x: Int, testReporter: org.junit.jupiter.api.TestReporter) { 
          System.out.println("${'$'}x, ${'$'}testReporter") 
        }

        companion object {
          @JvmStatic
          fun stream(): java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments>? { return null }
          
          @JvmStatic
          fun iterable(): Iterable<org.junit.jupiter.params.provider.Arguments>? { return null }
          
          @JvmStatic
          fun argumentsArrayProvider(): Array<org.junit.jupiter.params.provider.Arguments> { 
            return arrayOf(org.junit.jupiter.params.provider.Arguments.of(1, "one"))
          }
          
          @JvmStatic
          fun anyArrayProvider(): Array<Any> { return arrayOf(org.junit.jupiter.params.provider.Arguments.of(1, "one")) }
          
          @JvmStatic
          fun any2DArrayProvider(): Array<Array<Any>> { return arrayOf(arrayOf(1, "s")) }
          
          @JvmStatic
          fun intStreamProvider(): java.util.stream.IntStream? { return null }
        }
      }
      
      @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
      class TestWithMethodSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("getParameters")
        public fun shouldExecuteWithParameterizedMethodSource(arguments: String) { }
      
        public fun getParameters(): java.util.stream.Stream<String> { return java.util.Arrays.asList( "Another execution", "Last execution").stream() }
      }
      
      class JUnit3TestWithMethodSource : junit.framework.TestCase() {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("bar")
        fun testX(foo: String, bar: String) { }
              
        companion object {
        @JvmStatic
        fun bar(): java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> = 
          java.util.stream.Stream.of(org.junit.jupiter.params.provider.Arguments.of("a", "b"))
        }
      }
      
      class EnumSource { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(names = ["FIRST"])
        fun runTest(value: TestEnum) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = TestEnum::class,
          names = ["regexp-value"],
          mode = org.junit.jupiter.params.provider.EnumSource.Mode.MATCH_ALL
        )
        fun disable() { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(value = TestEnum::class, names = ["SECOND", "FIRST"/*, "commented"*/])
        fun array() {  }        
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(TestEnum::class)
        fun testWithEnumSourceCorrect(value: TestEnum) { }        
      }
      
      class CsvSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource(value = ["src, 1"])
        fun testWithCsvSource(first: String, second: Int) { }  
      }
      
      class NullSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.NullSource
        fun testWithNullSrc(o: Any) { }      
      }
      
      class EmptySource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        fun testFooSet(input: Set<String>) {}

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        fun testFooList(input: List<String>) {}

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        fun testFooMap(input: Map<String, String>) {}  
      }
    """.trimIndent()
    )
  }
  fun `test malformed parameterized value source wrong type`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(booleans = [
          <warning descr="No implicit conversion found to convert 'boolean' to 'int'">false</warning>
        ])
        fun testWithBooleanSource(argument: Int) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized enum source wrong type`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      enum class TestEnum { FIRST, SECOND, THIRD }
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(<warning descr="No implicit conversion found to convert 'TestEnum' to 'int'">TestEnum::class</warning>)
        fun testWithEnumSource(i: Int) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized multiple types`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.<warning descr="Exactly one type of input must be provided">ValueSource</warning>(
          ints = [1], strings = ["str"]
        )
        fun testWithMultipleValues(i: Int) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized no value defined`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.<warning descr="No value source is defined">ValueSource</warning>()
        fun testWithNoValues(i: Int) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized no argument defined`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        <warning descr="'@NullSource' cannot provide an argument to method because method doesn't have parameters">@org.junit.jupiter.params.provider.NullSource</warning>
        fun testWithNullSrcNoParam() {}
      }
    """.trimIndent())
  }
  
  fun `test method source in another class`() {
    myFixture.addFileToProject("SampleTest.kt", """"
        open class SampleTest {
          companion object {
              @kotlin.jvm.JvmStatic
              fun squares() : List<org.junit.jupiter.params.provider.Arguments> {
                  return listOf(
                      org.junit.jupiter.params.provider.Arguments.of(1, 1)
                  )
              }
          }
      }""".trimIndent())
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;

      class MethodSourceUsage {
        @ParameterizedTest
        @MethodSource("SampleTest#squares")
        void testSquares(int input, int expected) {}
      }
    """.trimIndent())
  }
  
  fun `test malformed parameterized value source multiple parameters`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = ["foo"])
        fun <warning descr="Multiple parameters are not supported by this source">testWithMultipleParams</warning>(argument: String, i: Int) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized and test annotation defined`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(ints = [1])
        @org.junit.jupiter.api.Test
        fun <warning descr="Suspicious combination of '@Test' and '@ParameterizedTest'">testWithTestAnnotation</warning>(i: Int) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized and value source defined`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.provider.ValueSource(ints = [1])
        @org.junit.jupiter.api.Test
        fun <warning descr="Suspicious combination of '@ValueSource' and '@Test'">testWithTestAnnotationNoParameterized</warning>(i: Int) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized no argument source provided`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ArgumentsSources()
        fun <warning descr="No sources are provided, the suite would be empty">emptyArgs</warning>(param: String) { }
      }        
    """.trimIndent())
  }
  fun `test malformed parameterized method source should be static`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("<warning descr="Method source 'a' must be static">a</warning>")
        fun foo(param: String) { }
        
        fun a(): Array<String> { return arrayOf("a", "b") }
      }        
    """.trimIndent())
  }
  fun `test malformed parameterized method source should have no parameters`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("<warning descr="Method source 'a' should have no parameters">a</warning>")
        fun foo(param: String) { }
        
        companion object {
          @JvmStatic
          fun a(i: Int): Array<String> { return arrayOf("a", i.toString()) }
        }
      }        
    """.trimIndent())
  }

  fun `test malformed parameterized method source wrong return type`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(
          "<warning descr="Method source 'a' must have one of the following return types: 'Stream<?>', 'Iterator<?>', 'Iterable<?>' or 'Object[]'">a</warning>"
        )
        fun foo(param: String) { }
        
        companion object {
          @JvmStatic
          fun a(): Any { return arrayOf("a", "b") }
        }
      }        
    """.trimIndent())
  }
  fun `test malformed parameterized method source not found`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("<warning descr="Cannot resolve target method source: 'a'">a</warning>")
        fun foo(param: String) { }
      }        
    """.trimIndent())
  }
  fun `test malformed parameterized enum source unresolvable entry`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class EnumSourceTest {
        private enum class Foo { AAA, AAX, BBB }
      
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo::class, 
          names = ["<warning descr="Can't resolve 'enum' constant reference.">invalid-value</warning>"], 
          mode = org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE
        )
        fun invalid() { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo::class, 
          names = ["<warning descr="Can't resolve 'enum' constant reference.">invalid-value</warning>"]
       )
        fun invalidDefault() { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized add test instance quick fix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.Arguments
      import org.junit.jupiter.params.provider.MethodSource
      
      import java.util.stream.Stream;
      
      class Test {
        private fun parameters(): Stream<Arguments>?  { return null; }
      
        @MethodSource("param<caret>eters")
        @ParameterizedTest
        fun foo(param: String) { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.TestInstance
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.Arguments
      import org.junit.jupiter.params.provider.MethodSource
      
      import java.util.stream.Stream;
      
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      class Test {
        private fun parameters(): Stream<Arguments>?  { return null; }
      
        @MethodSource("parameters")
        @ParameterizedTest
        fun foo(param: String) { }
      }
    """.trimIndent(), "Annotate as @TestInstance")
  }
  fun `test malformed parameterized introduce method source quick fix`() {
    myFixture.testQuickFix(JvmLanguage.KOTLIN, """
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.MethodSource
      
      class Test {
        @MethodSource("para<caret>meters")
        @ParameterizedTest
        fun foo(param: String) { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.params.ParameterizedTest
      import org.junit.jupiter.params.provider.Arguments
      import org.junit.jupiter.params.provider.MethodSource
      import java.util.stream.Stream
      
      class Test {
        @MethodSource("parameters")
        @ParameterizedTest
        fun foo(param: String) { }

          companion object {
              @JvmStatic
              fun parameters(): Stream<Arguments> {
                  TODO("Not yet implemented")
              }
          }
      }
    """.trimIndent(), "Add method 'parameters' to 'Test'") // TODO make createMethod preview work
  }
  fun `test malformed parameterized create csv source quick fix`() {
    val file = myFixture.addFileToProject("CsvFile.kt", """
        class CsvFile {
            @org.junit.jupiter.params.ParameterizedTest
            @org.junit.jupiter.params.provider.CsvFileSource(resources = "two-<caret>column.txt")
            fun testWithCsvFileSource(first: String, second: Int) { }
        }
    """.trimIndent())
    myFixture.configureFromExistingVirtualFile(file.virtualFile)
    val intention = myFixture.findSingleIntention("Create file two-column.txt")
    assertNotNull(intention)
    myFixture.launchAction(intention)
    assertNotNull(myFixture.findFileInTempDir("two-column.txt"))
  }

  /* Malformed repeated test*/
  fun `test malformed repeated test no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      object WithRepeated {
        @org.junit.jupiter.api.RepeatedTest(1)
        fun repeatedTestNoParams() { }

        @org.junit.jupiter.api.RepeatedTest(1)
        fun repeatedTestWithRepetitionInfo(repetitionInfo: org.junit.jupiter.api.RepetitionInfo) { }  

        @org.junit.jupiter.api.BeforeEach
        fun config(repetitionInfo: org.junit.jupiter.api.RepetitionInfo) { }
      }

      class WithRepeatedAndCustomNames {
        @org.junit.jupiter.api.RepeatedTest(value = 1, name = "{displayName} {currentRepetition}/{totalRepetitions}")
        fun repeatedTestWithCustomName() { }
      }

      class WithRepeatedAndTestInfo {
        @org.junit.jupiter.api.BeforeEach
        fun beforeEach(testInfo: org.junit.jupiter.api.TestInfo, repetitionInfo: org.junit.jupiter.api.RepetitionInfo) {}

        @org.junit.jupiter.api.RepeatedTest(1)
        fun repeatedTestWithTestInfo(testInfo: org.junit.jupiter.api.TestInfo) { }

        @org.junit.jupiter.api.AfterEach
        fun afterEach(testInfo: org.junit.jupiter.api.TestInfo, repetitionInfo: org.junit.jupiter.api.RepetitionInfo) {}
      }

      class WithRepeatedAndTestReporter {
        @org.junit.jupiter.api.BeforeEach
        fun beforeEach(testReporter: org.junit.jupiter.api.TestReporter, repetitionInfo: org.junit.jupiter.api.RepetitionInfo) {}

        @org.junit.jupiter.api.RepeatedTest(1)
        fun repeatedTestWithTestInfo(testReporter: org.junit.jupiter.api.TestReporter) { }

        @org.junit.jupiter.api.AfterEach
        fun afterEach(testReporter: org.junit.jupiter.api.TestReporter, repetitionInfo: org.junit.jupiter.api.RepetitionInfo) {}
      }
    """.trimIndent())
  }
  fun `test malformed repeated test combination of @Test and @RepeatedTest`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class WithRepeatedAndTests {
        @org.junit.jupiter.api.Test
        @org.junit.jupiter.api.RepeatedTest(1)
        fun <warning descr="Suspicious combination of '@Test' and '@RepeatedTest'">repeatedTestAndTest</warning>() { }
      }
    """.trimIndent())
  }
  fun `test malformed repeated test with injected RepeatedInfo for @Test method`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class WithRepeatedInfoAndTest {
        @org.junit.jupiter.api.BeforeEach
        fun beforeEach(repetitionInfo: org.junit.jupiter.api.RepetitionInfo) { }

        @org.junit.jupiter.api.Test
        fun <warning descr="Method 'nonRepeated' annotated with '@Test' should not declare parameter 'repetitionInfo'">nonRepeated</warning>(repetitionInfo: org.junit.jupiter.api.RepetitionInfo) { }
      }      
    """.trimIndent() )
  }
  fun `test malformed repeated test with injected RepetitionInfo for @BeforeAll method`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class WithBeforeEach {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun <warning descr="Method 'beforeAllWithRepetitionInfo' annotated with '@BeforeAll' should not declare parameter 'repetitionInfo'">beforeAllWithRepetitionInfo</warning>(repetitionInfo: org.junit.jupiter.api.RepetitionInfo) { }
        }
      }
    """.trimIndent())
  }
  fun `test malformed repeated test with non-positive repetitions`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class WithRepeated {
        @org.junit.jupiter.api.RepeatedTest(<warning descr="The number of repetitions must be greater than zero">-1</warning>)
        fun repeatedTestNegative() { }

        @org.junit.jupiter.api.RepeatedTest(<warning descr="The number of repetitions must be greater than zero">0</warning>)
        fun repeatedTestBoundaryZero() { }
      }
    """.trimIndent())
  }

  /* Malformed before after */
  fun `test malformed before highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        @org.junit.Before
        fun <warning descr="Method 'before' annotated with '@Before' should be of type 'void' and not declare parameter 'i'">before</warning>(i: Int): String { return "${'$'}i" }
      }
    """.trimIndent())
  }
  fun `test malformed before each highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        fun <warning descr="Method 'beforeEach' annotated with '@BeforeEach' should be of type 'void' and not declare parameter 'i'">beforeEach</warning>(i: Int): String { return "" }
      }
    """.trimIndent())
  }
  fun `test malformed before each remove private quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        private fun bef<caret>oreEach() { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.BeforeEach
      
      class MainTest {
        @BeforeEach
        fun beforeEach(): Unit { }
      }
    """.trimIndent(), "Fix 'beforeEach' method signature")
  }
  fun `test malformed before class no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class BeforeClassStatic {
        companion object {
          @JvmStatic
          @org.junit.BeforeClass
          fun beforeClass() { }
        }
      }
      
      @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
      class BeforeAllTestInstancePerClass {
        @org.junit.jupiter.api.BeforeAll
        fun beforeAll() { }
      }
      
      class BeforeAllStatic {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun beforeAll() { }
        }
      }
      
      class TestParameterResolver : org.junit.jupiter.api.extension.ParameterResolver {
        override fun supportsParameter(
          parameterContext: org.junit.jupiter.api.extension.ParameterContext, 
          extensionContext: org.junit.jupiter.api.extension.ExtensionContext
        ): Boolean = true
    
        override fun resolveParameter(
          parameterContext: org.junit.jupiter.api.extension.ParameterContext, 
          extensionContext: org.junit.jupiter.api.extension.ExtensionContext
        ): Any = ""
      }
      
      @org.junit.jupiter.api.extension.ExtendWith(TestParameterResolver::class)
      class ParameterResolver {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun beforeAll(foo: String) { println(foo) }
        }
      }
      
      @org.junit.jupiter.api.extension.ExtendWith(value = [TestParameterResolver::class])
      class ParameterResolverArray {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun beforeAll(foo: String) { println(foo) }
        }
      }
      
      @org.junit.jupiter.api.extension.ExtendWith(TestParameterResolver::class)
      open class AbstractTest { }
      
      class TestImplementation: AbstractTest() {
          @org.junit.jupiter.api.BeforeEach
          fun beforeEach(valueBox : String){ }
      }
      
      @org.junit.jupiter.api.extension.ExtendWith(TestParameterResolver::class)
      annotation class CustomTestAnnotation
      
      @CustomTestAnnotation
      class MetaParameterResolver {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun beforeAll(foo: String) { println(foo) }
        }
      }
    """.trimIndent())
  }
  fun `test malformed before class method that is non-static`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        @org.junit.BeforeClass
        fun <warning descr="Method 'beforeClass' annotated with '@BeforeClass' should be static">beforeClass</warning>() { }
      }
    """.trimIndent())
  }
  fun `test malformed before class method that is not private`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.BeforeClass
          private fun <warning descr="Method 'beforeClass' annotated with '@BeforeClass' should be public">beforeClass</warning>() { }
        }
      }
    """.trimIndent())
  }
  fun `test malformed before class method that has parameters`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.BeforeClass
          fun <warning descr="Method 'beforeClass' annotated with '@BeforeClass' should not declare parameter 'i'">beforeClass</warning>(i: Int) { System.out.println(i) }
        }
      }
    """.trimIndent())
  }
  fun `test malformed before class method with a non void return type`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.BeforeClass
          fun <warning descr="Method 'beforeClass' annotated with '@BeforeClass' should be of type 'void'">beforeClass</warning>(): String { return "" }
        }
      }
    """.trimIndent())
  }
  fun `test malformed before all method that is non-static`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        fun <warning descr="Method 'beforeAll' annotated with '@BeforeAll' should be static">beforeAll</warning>() { }
      }
    """.trimIndent())
  }
  fun `test malformed before all method that is not private`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          private fun <warning descr="Method 'beforeAll' annotated with '@BeforeAll' should be public">beforeAll</warning>() { }
        }
      }
    """.trimIndent())
  }
  fun `test malformed before all method that has parameters`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun <warning descr="Method 'beforeAll' annotated with '@BeforeAll' should not declare parameter 'i'">beforeAll</warning>(i: Int) { System.out.println(i) }
        }
      }
    """.trimIndent())
  }
  fun `test malformed before all method with a non void return type`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        companion object {
          @JvmStatic
          @org.junit.jupiter.api.BeforeAll
          fun <warning descr="Method 'beforeAll' annotated with '@BeforeAll' should be of type 'void'">beforeAll</warning>(): String { return "" }
        }
      }
    """.trimIndent())
  }
  fun `test malformed before all quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
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
  fun `test no highlighting when automatic registered parameter resolver is found`() {
    myFixture.addFileToProject("com/intellij/testframework/ext/AutomaticExtension.kt", """
      package com.intellij.testframework.ext
      
      class AutomaticExtension : org.junit.jupiter.api.extension.ParameterResolver {
        override fun supportsParameter(
          parameterContext: org.junit.jupiter.api.extension.ParameterContext, 
          extensionContext: org.junit.jupiter.api.extension.ExtensionContext
        ): Boolean = true
    
        override fun resolveParameter(
          parameterContext: org.junit.jupiter.api.extension.ParameterContext, 
          extensionContext: org.junit.jupiter.api.extension.ExtensionContext
        ): Any = ""
      }    
    """.trimIndent())
    addAutomaticExtension("com.intellij.testframework.ext.AutomaticExtension")
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        fun foo(x: Int) { }
      }
    """.trimIndent())
  }

  /* Malformed datapoint(s) */
  fun `test malformed datapoint no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test {
        companion object {
          @JvmField
          @org.junit.experimental.theories.DataPoint
          val f1: Any? = null
        }
      }
    """.trimIndent())
  }
  fun `test malformed datapoint non-static highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test {
         @JvmField
         @org.junit.experimental.theories.DataPoint
         val <warning descr="Field 'f1' annotated with '@DataPoint' should be static">f1</warning>: Any? = null
      }
    """.trimIndent())
  }
  fun `test malformed datapoint non-public highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test {
        companion object {
          @JvmStatic
          @org.junit.experimental.theories.DataPoint
          private val <warning descr="Field 'f1' annotated with '@DataPoint' should be public">f1</warning>: Any? = null
        }
      }
    """.trimIndent())
  }
  fun `test malformed datapoint field highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test {
         @org.junit.experimental.theories.DataPoint
         private val <warning descr="Field 'f1' annotated with '@DataPoint' should be static and public">f1</warning>: Any? = null
      }
    """.trimIndent())
  }
  fun `test malformed datapoint method highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test {
         @org.junit.experimental.theories.DataPoint
         private fun <warning descr="Method 'f1' annotated with '@DataPoint' should be static and public">f1</warning>(): Any? = null
      }
    """.trimIndent())
  }
  fun `test malformed datapoints method highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Test {
         @org.junit.experimental.theories.DataPoints
         private fun <warning descr="Method 'f1' annotated with '@DataPoints' should be static and public">f1</warning>(): Any? = null
      }
    """.trimIndent())
  }
  fun `test malformed datapoint make field public quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      class Test {
          companion object {
              @org.junit.experimental.theories.DataPoint
              val f<caret>1: Any? = null
          }
      }
    """.trimIndent(), """
      class Test {
          companion object {
              @JvmField
              @org.junit.experimental.theories.DataPoint
              val f1: Any? = null
          }
      }
    """.trimIndent(), "Fix 'f1' field signature")
  }
  fun `test malformed datapoint make field public and static quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      class Test {
          @org.junit.experimental.theories.DataPoint
          val f<caret>1: Any? = null
      }
    """.trimIndent(), """
      class Test {
          companion object {
              @JvmField
              @org.junit.experimental.theories.DataPoint
              val f1: Any? = null
          }
      }
    """.trimIndent(), "Fix 'f1' field signature")
  }
  fun `test malformed datapoint make method public and static quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      class Test {
          @org.junit.experimental.theories.DataPoint
          private fun f<caret>1(): Any? = null
      }
    """.trimIndent(), """
      class Test {
          companion object {
              @JvmStatic
              @org.junit.experimental.theories.DataPoint
              fun f1(): Any? = null
          }
      }
    """.trimIndent(), "Fix 'f1' method signature")
  }

  /* Malformed setup/teardown */
  fun `test malformed setup no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class C : junit.framework.TestCase() {
        override fun setUp() { }
      }  
    """.trimIndent())
  }
  fun `test malformed setup highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class C : junit.framework.TestCase() {
        private fun <warning descr="Method 'setUp' should be non-private, non-static, have no parameters and of type void">setUp</warning>(i: Int) { System.out.println(i) }
      }  
    """.trimIndent())
  }
  fun `test malformed setup quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      class C : junit.framework.TestCase() {
        private fun set<caret>Up(i: Int) { }
      }  
    """.trimIndent(), """
      class C : junit.framework.TestCase() {
        fun setUp(): Unit { }
      }  
    """.trimIndent(), "Fix 'setUp' method signature")
  }


  /* Malformed rule */
  fun `test malformed rule field non-public highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class SomeTestRule : org.junit.rules.TestRule {
        override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement = base
      }

      class PrivateRule {
        @org.junit.Rule
        var <warning descr="Field 'x' annotated with '@Rule' should be public">x</warning> = SomeTestRule()
      }
    """.trimIndent())
  }
  fun `test malformed rule object inherited rule highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class SomeTestRule : org.junit.rules.TestRule {
        override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement = base
      }  
      
      object OtherRule : org.junit.rules.TestRule { 
        override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement = base
      }
      
      object ObjRule {
        @org.junit.Rule
        private var <warning descr="Field 'x' annotated with '@Rule' should be non-static and public">x</warning> = SomeTestRule()
      }

      class ClazzRule {
        @org.junit.Rule
        fun x() = OtherRule
        
        @org.junit.Rule
        fun <warning descr="Method 'y' annotated with '@Rule' should be of type 'org.junit.rules.TestRule'">y</warning>() = 0

        @org.junit.Rule
        public fun z() = object : org.junit.rules.TestRule {
          override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement = base
        }

        @org.junit.Rule
        public fun <warning descr="Method 'a' annotated with '@Rule' should be of type 'org.junit.rules.TestRule'">a</warning>() = object { }
      }  
    """.trimIndent())
  }
  fun `test malformed rule method static highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class SomeTestRule : org.junit.rules.TestRule {
        override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement = base
      }
      
      class PrivateRule {
        @org.junit.Rule
        private fun <warning descr="Method 'x' annotated with '@Rule' should be public">x</warning>() = SomeTestRule()
      }
    """.trimIndent())
  }
  fun `test malformed rule method non TestRule type highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class PrivateRule {
        @org.junit.Rule
        fun <warning descr="Method 'x' annotated with '@Rule' should be of type 'org.junit.rules.TestRule'">x</warning>() = 0
      }
    """.trimIndent())
  }
  fun `test malformed class rule field highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class SomeTestRule : org.junit.rules.TestRule {
        override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement = base
      }  
      
      object PrivateClassRule {
        @org.junit.ClassRule
        private var <warning descr="Field 'x' annotated with '@ClassRule' should be public">x</warning> = SomeTestRule()
      
        @org.junit.ClassRule
        private var <warning descr="Field 'y' annotated with '@ClassRule' should be public and of type 'org.junit.rules.TestRule'">y</warning> = 0
      }
    """.trimIndent())
  }
  fun `test malformed rule make field public quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      class PrivateRule {
        @org.junit.Rule
        var x<caret> = 0
      }
    """.trimIndent(), """
      class PrivateRule {
        @JvmField
        @org.junit.Rule
        var x = 0
      }
    """.trimIndent(), "Fix 'x' field signature")
  }
  fun `test malformed class rule make field public quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      class SomeTestRule : org.junit.rules.TestRule {
          override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement = base
      }

      object PrivateClassRule {
          @org.junit.ClassRule
          private var x<caret> = SomeTestRule()
      }
    """.trimIndent(), """
      class SomeTestRule : org.junit.rules.TestRule {
          override fun apply(base: org.junit.runners.model.Statement, description: org.junit.runner.Description): org.junit.runners.model.Statement = base
      }

      object PrivateClassRule {
          @JvmField
          @org.junit.ClassRule
          var x = SomeTestRule()
      }
    """.trimIndent(), "Fix 'x' field signature")
  }

  /* Malformed test */
  fun `test malformed test for JUnit 3 highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class JUnit3TestMethodIsPublicVoidNoArg : junit.framework.TestCase() {
        fun testOne() { }
        public fun <warning descr="Method 'testTwo' should be public, non-static, have no parameters and of type void">testTwo</warning>(): Int { return 2 }
        public fun <warning descr="Method 'testFour' should be public, non-static, have no parameters and of type void">testFour</warning>(i: Int) { println(i) }
        public fun testFive() { }
        private fun testSix(i: Int) { println(i) } //ignore when method doesn't look like test anymore
        companion object {
          @JvmStatic
          public fun <warning descr="Method 'testThree' should be public, non-static, have no parameters and of type void">testThree</warning>() { }
        }
      }
    """.trimIndent())
  }
  fun `test malformed test for JUnit 4 highlighting`() {
    myFixture.addClass("""
      package mockit;
      public @interface Mocked { }
    """.trimIndent())
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      public class JUnit4TestMethodIsPublicVoidNoArg {
        @org.junit.Test fun testOne() { }
        @org.junit.Test public fun <warning descr="Method 'testTwo' annotated with '@Test' should be of type 'void'">testTwo</warning>(): Int { return 2 }
        @org.junit.Test public fun <warning descr="Method 'testFour' annotated with '@Test' should not declare parameter 'i'">testFour</warning>(i: Int) { }
        @org.junit.Test public fun testFive() { }
        @org.junit.Test public fun testMock(@mockit.Mocked s: String) { }
        companion <warning descr="Test class 'object' is not constructable because it should have exactly one 'public' no-arg constructor">object</warning> {
          @JvmStatic
          @org.junit.Test public fun <warning descr="Method 'testThree' annotated with '@Test' should be non-static">testThree</warning>() { }
        }
      }
    """.trimIndent())
  }
  fun `test no highlighting on custom runner`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MyRunner : org.junit.runner.Runner() {
          override fun getDescription(): org.junit.runner.Description? { return null }

          override fun run(notifier: org.junit.runner.notification.RunNotifier) { }
      }
      
      @org.junit.runner.RunWith(MyRunner::class)
      class Foo {
          public fun testMe(i: Int): Int { return -1 }
      }
    """.trimIndent())
  }
  fun `test highlighting on predefined runner`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      @org.junit.runner.RunWith(org.junit.runners.JUnit4::class)
      class Foo {
          @org.junit.Test public fun <warning descr="Method 'testMe' annotated with '@Test' should be of type 'void' and not declare parameter 'i'">testMe</warning>(i: Int): Int { return -1 }
      }
    """.trimIndent())
  }

  /* Malformed suite */
  fun `test malformed suite no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Junit3Suite : junit.framework.TestCase() {          
          companion object {
              @JvmStatic  
              fun suite() = junit.framework.TestSuite()
          }
      }
    """.trimIndent())
  }
  fun `test malformed suite highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Junit3Suite : junit.framework.TestCase() {          
          fun <warning descr="Method 'suite' should be non-private, static and have no parameters">suite</warning>() = junit.framework.TestSuite()
      }
    """.trimIndent())
  }
  fun `test malformed suite quickfix`() {
    myFixture.testQuickFixWithPreview(JvmLanguage.KOTLIN, """
      class Junit3Suite : junit.framework.TestCase() {          
          fun sui<caret>te() = junit.framework.TestSuite()
      }
    """.trimIndent(), """
      class Junit3Suite : junit.framework.TestCase() {
          companion object {
              @JvmStatic
              fun suite() = junit.framework.TestSuite()
          }
      }
    """.trimIndent(), "Fix 'suite' method signature")
  }

  /* Suspending test function */
  fun `test malformed suspending test JUnit 3 function`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class JUnit3Test : junit.framework.TestCase() {
          suspend fun <warning descr="Method 'testFoo' should not be a suspending function">testFoo</warning>() { }
      }    
    """.trimIndent())
  }
  fun `test malformed suspending test JUnit 4 function`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class JUnit4Test {
          @org.junit.Test
          suspend fun <warning descr="Method 'testFoo' annotated with '@Test' should not be a suspending function">testFoo</warning>() { }
      }    
    """.trimIndent())
  }
  fun `test malformed suspending test JUnit 5 function`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class JUnit5Test {
          @org.junit.jupiter.api.Test
          suspend fun <warning descr="Method 'testFoo' annotated with '@Test' should not be a suspending function">testFoo</warning>() { }
      }    
    """.trimIndent())
  }

  fun `test extends with no highlighting`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class MockitoExtension : org.junit.jupiter.api.extension.BeforeEachCallback, org.junit.jupiter.api.extension.AfterEachCallback, org.junit.jupiter.api.extension.ParameterResolver {
        override fun beforeEach(context: org.junit.jupiter.api.extension.ExtensionContext?) { } 
        override fun afterEach(context: org.junit.jupiter.api.extension.ExtensionContext?) { }
        override fun supportsParameter(parameterContext: org.junit.jupiter.api.extension.ParameterContext?, extensionContext: org.junit.jupiter.api.extension.ExtensionContext?): Boolean { TODO() }
        override fun resolveParameter(parameterContext: org.junit.jupiter.api.extension.ParameterContext?, extensionContext: org.junit.jupiter.api.extension.ExtensionContext?): Any { TODO() }
      }
      
      class MyTest {
        @org.junit.jupiter.api.Test
        @org.junit.jupiter.api.extension.ExtendWith(MockitoExtension::class)
        fun testFoo(x: String) { }
      }
    """.trimIndent())
  }

  // Unconstructable test case
  fun testPlain() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      class Plain { }
    """.trimIndent())
  }
  fun testUnconstructableJUnit3TestCase1() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import junit.framework.TestCase

      class <warning descr="Test class 'UnconstructableJUnit3TestCase1' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableJUnit3TestCase1</warning> private constructor() : TestCase() {
      }
    """.trimIndent())
  }
  fun testUnconstructableJUnit3TestCase2() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import junit.framework.TestCase

      class <warning descr="Test class 'UnconstructableJUnit3TestCase2' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableJUnit3TestCase2</warning>(val foo: Any) : TestCase() {
        fun bar() { }
      }
    """.trimIndent())
  }
  fun testUnconstructableJUnit3TestCase3() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import junit.framework.TestCase

      class UnconstructableJUnit3TestCase3() : TestCase() { }
    """.trimIndent())
  }
  fun testUnconstructableJUnit3TestCase4() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import junit.framework.TestCase

      class UnconstructableJUnit3TestCase4(val foo: String) : TestCase() { }
    """.trimIndent())
  }
  fun testUnconstructableJUnit3TestCaseAnynoymousObject() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import junit.framework.TestCase
      
      class Test {
          private val testCase = object : TestCase() { }
      }
    """.trimIndent())
  }
  fun testUnconstructableJUnit3TestCaseLocalClass() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import junit.framework.TestCase
      
      fun main () {
        class LocalClass : TestCase() { }
      }
    """.trimIndent())
  }
  fun testUnconstructableJUnit4TestCase1() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.Test
      
      class <warning descr="Test class 'UnconstructableJUnit4TestCase1' is not constructable because it should have exactly one 'public' no-arg constructor">UnconstructableJUnit4TestCase1</warning>() {
        constructor(args: String) : this() { args.plus("") }
      
        @Test
        fun testMe() {}
      }
    """.trimIndent())
  }
  fun testUnconstructableJUnit4TestCase2() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.Test

      class UnconstructableJUnit4TestCase2() {
        private constructor(one: String, two: String) : this() { one.plus(two) }

      	@Test
      	public fun testAssertion() { }
      }
    """.trimIndent())
  }
  fun testUnconstructableJUnit4TestCase3() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.junit.Test

      private class <warning descr="Test class 'UnconstructableJUnit4TestCase3' is not constructable because it is not 'public'">UnconstructableJUnit4TestCase3</warning>() {

        @Test
        fun testMe() {}
      }
    """.trimIndent())
  }
}