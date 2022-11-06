package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitMalformedDeclarationInspectionTestBase

class JavaJUnitMalformedDeclarationInspectionTest : JUnitMalformedDeclarationInspectionTestBase() {
  /* Malformed extensions */
  fun `test malformed extension no highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        Rule5 myRule5 = new Rule5();
        class Rule5 implements org.junit.jupiter.api.extension.Extension { }
      }
    """.trimIndent())
  }
  fun `test malformed extension subtype highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        Rule5 <warning descr="Field 'myRule5' annotated with '@RegisterExtension' should be of type 'org.junit.jupiter.api.extension.Extension'">myRule5</warning> = new Rule5();
        class Rule5 { }
      }
    """.trimIndent())
  }
  fun `test malformed private highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.extension.RegisterExtension
        private Rule5 <warning descr="Field 'myRule5' annotated with '@RegisterExtension' should be public">myRule5</warning> = new Rule5();
        class Rule5 implements org.junit.jupiter.api.extension.Extension { }
      }
    """.trimIndent())
  }

  /* Malformed nested class */
  fun `test malformed nested class no highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.Nested
        class B { }
      }
    """.trimIndent())
  }
  fun `test malformed nested class highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.Nested
        static class <warning descr="Class 'B' annotated with '@Nested' should be non-static">B</warning> { }
        
        @org.junit.jupiter.api.Nested
        private class <warning descr="Class 'C' annotated with '@Nested' should be non-private">C</warning> { }
        
        @org.junit.jupiter.api.Nested
        private static class <warning descr="Class 'D' annotated with '@Nested' should be non-static and non-private">D</warning> { }
      }
    """.trimIndent())
  }
  fun `test malformed nested class quickfix`() {
    myFixture.testAllQuickfixes(ULanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.Nested
        static class B { }
        
        @org.junit.jupiter.api.Nested
        private class C { }
        
        @org.junit.jupiter.api.Nested
        private static class D { }
      }
    """.trimIndent(), """
      class A {
        @org.junit.jupiter.api.Nested
        class B { }
        
        @org.junit.jupiter.api.Nested
        public class C { }
        
        @org.junit.jupiter.api.Nested
        public class D { }
      }
    """.trimIndent(), "Fix class signature")
  }
  fun `test malformed nested class preview`() {
    myFixture.testPreview(ULanguage.JAVA, """
      class A {
        @org.junit.jupiter.api.Nested
        static class <caret>B { }
      }
    """.trimIndent(), """
      class A {
        @org.junit.jupiter.api.Nested
        class B { }
      }
    """.trimIndent(), "Fix 'B' class signature")
  }



  /* Malformed parameterized */
  fun `test malformed parameterized no highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      enum TestEnum { FIRST, SECOND, THIRD }
      
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(ints = {1})
        void testWithIntValues(int i) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(longs = {1L})
        void testWithIntValues(long i) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(doubles = {0.5})
        void testWithDoubleValues(double d) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {""})
        void testWithStringValues(String s) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = "foo")
        void implicitParameter(String argument, org.junit.jupiter.api.TestInfo testReporter) { }
        
        @org.junit.jupiter.api.extension.ExtendWith(org.junit.jupiter.api.extension.TestExecutionExceptionHandler.class)
        @interface RunnerExtension { }
      
        @RunnerExtension
        abstract class AbstractValueSource { }
        
        class ValueSourcesWithCustomProvider extends AbstractValueSource {
          @org.junit.jupiter.params.ParameterizedTest
          @org.junit.jupiter.params.provider.ValueSource(ints = {1})
          void testWithIntValues(int i, String fromExtension) { }
        }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = { "FIRST" })
        void implicitConversionEnum(TestEnum e) { }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = { "1" })
        void implicitConversionString(int i) { }
          
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = "title")
        void implicitConversionClass(Book book) { }

        static class Book { public Book(String title) { } }
      }
      
      class MethodSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("stream")
        void simpleStream(int x, int y) { System.out.println(x + ", " + y); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("iterable")
        void simpleIterable(int x, int y) { System.out.println(x + ", " + y); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("iterator")
        void simpleIterator(int x, int y) { System.out.println(x + ", " + y); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = {"stream", "iterator", "iterable"})
        void parametersArray(int x, int y) { System.out.println(x + ", " + y); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({"stream", "iterator"})
        void implicitValueArray(int x, int y) { System.out.println(x + ", " + y); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = "argumentsArrayProvider")
        void argumentsArray(int x, String s) { System.out.println(x + ", " + s); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = "objectsArrayProvider")
        void objectsArray(int x, String s) { System.out.println(x + ", " + s); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource(value = "objects2DArrayProvider")
        void objects2DArray(int x, String s) { System.out.println(x + ", " + s); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("intStreamProvider")
        void intStream(int x) { System.out.println(x); }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("intStreamProvider")
        void injectTestReporter(int x, org.junit.jupiter.api.TestReporter testReporter) { System.out.println(x); }

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> stream() { return null; }
        static java.util.Iterator<org.junit.jupiter.params.provider.Arguments> iterator() { return null; }
        static Iterable<org.junit.jupiter.params.provider.Arguments> iterable() { return null; }
        static org.junit.jupiter.params.provider.Arguments[] argumentsArrayProvider() { 
          return new org.junit.jupiter.params.provider.Arguments[] { org.junit.jupiter.params.provider.Arguments.of(1, "one") }; 
        }
        static Object[] objectsArrayProvider() { return new Object[] { org.junit.jupiter.params.provider.Arguments.of(1, "one") }; }
        static Object[][] objects2DArrayProvider() { return new Object[][] { {1, "s"} }; }
        static java.util.stream.IntStream intStreamProvider() { return null; }
      }
      
      @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
      class TestWithMethodSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource("getParameters")
        public void shouldExecuteWithParameterizedMethodSource(String arguments) { }
      
        public java.util.stream.Stream getParameters() { return java.util.Arrays.asList( "Another execution", "Last execution").stream(); }
      }
      
      class EnumSource { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(names = "FIRST")
        void runTest(TestEnum value) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = TestEnum.class,
          names = "regexp-value",
          mode = org.junit.jupiter.params.provider.EnumSource.Mode.MATCH_ALL
        )
        void disable() { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(value = TestEnum.class, names = {"SECOND", "FIRST"/*, "commented"*/})
        void array() {  }        
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(TestEnum.class)
        void testWithEnumSourceCorrect(TestEnum value) { }        
      }
      
      class CsvSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource(value = "src, 1")
        void testWithCsvSource(String first, int second) { }  
      }
      
      class NullSource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.NullSource
        void testWithNullSrc(Object o) { }      
      }
      
      class EmptySource {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testFooSet(java.util.Set<String> input) { }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testFooList(java.util.List<String> input) { }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testFooMap(java.util.Map<String, String> input) { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EmptySource
        void testFooTestInfo(String input, org.junit.jupiter.api.TestInfo testInfo) { }
      }
    """.trimIndent()
    )
  }
  fun `test malformed parameterized value source wrong type highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(booleans = {
          <warning descr="No implicit conversion found to convert 'boolean' to 'int'">false</warning>
        })
        void testWithBooleanSource(int argument) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized enum source wrong type highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      enum TestEnum { FIRST, SECOND, THIRD }
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(<warning descr="No implicit conversion found to convert 'TestEnum' to 'int'">TestEnum.class</warning>)
        void testWithEnumSource(int i) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized multiple types highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest {
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.<warning descr="Exactly one type of input must be provided">ValueSource</warning>(
          ints = {1}, strings = "str"
        )
        void testWithMultipleValues(int i) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized no value defined highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.<warning descr="No value source is defined">ValueSource</warning>()
        void testWithNoValues(int i) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized no argument defined highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        <warning descr="'@NullSource' cannot provide an argument to method because method doesn't have parameters">@org.junit.jupiter.params.provider.NullSource</warning>
        void testWithNullSrcNoParam() {}
      }
    """.trimIndent())
  }
  fun `test malformed parameterized value source multiple parameters highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = "foo")
        void <warning descr="Multiple parameters are not supported by this source">testWithMultipleParams</warning>(String argument, int i) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized and test annotation defined highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(ints = {1})
        @org.junit.jupiter.api.Test
        void <warning descr="Suspicious combination of '@Test' and '@ParameterizedTest'">testWithTestAnnotation</warning>(int i) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized and value source defined highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest { 
        @org.junit.jupiter.params.provider.ValueSource(ints = {1})
        @org.junit.jupiter.api.Test
        void <warning descr="Suspicious combination of '@ValueSource' and '@Test'">testWithTestAnnotationNoParameterized</warning>(int i) { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized no argument source provided highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ArgumentsSources({})
        void <warning descr="No sources are provided, the suite would be empty">emptyArgs</warning>(String param) { }
      }        
    """.trimIndent())
  }
  fun `test malformed parameterized method source should be static highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <warning descr="Method source 'a' must be static">"a"</warning> })
        void foo(String param) { }
        
        String[] a() { return new String[] {"a", "b"}; }
      }        
    """.trimIndent())
  }
  fun `test malformed parameterized method source should have no parameters highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <warning descr="Method source 'a' should have no parameters">"a"</warning> })
        void foo(String param) { }
        
        static String[] a(int i) { return new String[] {"a", "b"}; }
      }        
    """.trimIndent())
  }
  fun `test malformed parameterized method source wrong return type highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <warning descr="Method source 'a' must have one of the following return types: 'Stream<?>', 'Iterator<?>', 'Iterable<?>' or 'Object[]'">"a"</warning> })
        void foo(String param) { }
        
        static Object a() { return new String[] {"a", "b"}; }
      }        
    """.trimIndent())
  }
  fun `test malformed parameterized method source not found highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class ValueSourcesTest {       
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.MethodSource({ <warning descr="Cannot resolve target method source: 'a'">"a"</warning> })
        void foo(String param) { }
      }        
    """.trimIndent())
  }
  fun `test malformed parameterized enum source unresolvable entry highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class EnumSourceTest {
        private enum Foo { AAA, AAX, BBB }
      
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo.class, 
          names = <warning descr="Can't resolve 'enum' constant reference.">"invalid-value"</warning>, 
          mode = org.junit.jupiter.params.provider.EnumSource.Mode.INCLUDE
        )
        void invalid() { }
        
        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.EnumSource(
          value = Foo.class, 
          names = <warning descr="Can't resolve 'enum' constant reference.">"invalid-value"</warning>
       )
        void invalidDefault() { }
      }
    """.trimIndent())
  }
  fun `test malformed parameterized add test instance quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.Arguments;
      import org.junit.jupiter.params.provider.MethodSource;
      
      import java.util.stream.Stream;
      
      class Test {
        private Stream<Arguments> parameters() { return null; }
      
        @MethodSource("param<caret>eters")
        @ParameterizedTest
        void foo(String param) { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.TestInstance;
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.Arguments;
      import org.junit.jupiter.params.provider.MethodSource;
      
      import java.util.stream.Stream;
      
      @TestInstance(TestInstance.Lifecycle.PER_CLASS)
      class Test {
        private Stream<Arguments> parameters() { return null; }
      
        @MethodSource("parameters")
        @ParameterizedTest
        void foo(String param) { }
      }
    """.trimIndent(), "Annotate class 'Test' as '@TestInstance'")
  }
  fun `test malformed parameterized introduce method source quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.MethodSource;
      
      class Test {
        @MethodSource("para<caret>meters")
        @ParameterizedTest
        void foo(String param) { }
      }
    """.trimIndent(), """
      import org.junit.jupiter.params.ParameterizedTest;
      import org.junit.jupiter.params.provider.Arguments;
      import org.junit.jupiter.params.provider.MethodSource;
      
      import java.util.stream.Stream;
      
      class Test {
          public static Stream<Arguments> parameters() {
              return null;
          }
      
          @MethodSource("parameters")
        @ParameterizedTest
        void foo(String param) { }
      }
    """.trimIndent(), "Create method 'parameters' in 'Test'")
  }
  fun `test malformed parameterized create csv source quickfix`() {
    val file = myFixture.addFileToProject("CsvFile.java", """
        class CsvFile {
            @org.junit.jupiter.params.ParameterizedTest
            @org.junit.jupiter.params.provider.CsvFileSource(resources = "two-<caret>column.txt")
            void testWithCsvFileSource(String first, int second) { }
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
    myFixture.testHighlighting(ULanguage.JAVA, """
      class WithRepeated {
        @org.junit.jupiter.api.RepeatedTest(1)
        void repeatedTestNoParams() { }

        @org.junit.jupiter.api.RepeatedTest(1)
        void repeatedTestWithRepetitionInfo(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }

        @org.junit.jupiter.api.BeforeEach
        void config(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }
      }

      class WithRepeatedAndCustomNames {
        @org.junit.jupiter.api.RepeatedTest(value = 1, name = "{displayName} {currentRepetition}/{totalRepetitions}")
        void repeatedTestWithCustomName() { }
      }

      class WithRepeatedAndTestInfo {
        @org.junit.jupiter.api.BeforeEach
        void beforeEach(org.junit.jupiter.api.TestInfo testInfo, org.junit.jupiter.api.RepetitionInfo repetitionInfo) {}

        @org.junit.jupiter.api.RepeatedTest(1)
        void repeatedTestWithTestInfo(org.junit.jupiter.api.TestInfo testInfo) { }

        @org.junit.jupiter.api.AfterEach
        void afterEach(org.junit.jupiter.api.TestInfo testInfo, org.junit.jupiter.api.RepetitionInfo repetitionInfo) {}
      }

      class WithRepeatedAndTestReporter {
        @org.junit.jupiter.api.BeforeEach
        void beforeEach(org.junit.jupiter.api.TestReporter testReporter, org.junit.jupiter.api.RepetitionInfo repetitionInfo) {}

        @org.junit.jupiter.api.RepeatedTest(1)
        void repeatedTestWithTestInfo(org.junit.jupiter.api.TestReporter testReporter) { }

        @org.junit.jupiter.api.AfterEach
        void afterEach(org.junit.jupiter.api.TestReporter testReporter, org.junit.jupiter.api.RepetitionInfo repetitionInfo) {}
      }
    """.trimIndent())
  }
  fun `test malformed repeated test combination of @Test and @RepeatedTest highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class WithRepeatedAndTests {
        @org.junit.jupiter.api.Test
        @org.junit.jupiter.api.RepeatedTest(1)
        void <warning descr="Suspicious combination of '@Test' and '@RepeatedTest'">repeatedTestAndTest</warning>() { }
      }    
    """.trimIndent())
  }
  fun `test malformed repeated test with injected RepeatedInfo for @Test method highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class WithRepeatedInfoAndTest {
        @org.junit.jupiter.api.BeforeEach
        void beforeEach(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }

        @org.junit.jupiter.api.Test
        void <warning descr="Method 'nonRepeated' annotated with '@Test' should not declare parameter 'repetitionInfo'">nonRepeated</warning>(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }
      }      
    """.trimIndent())
  }
  fun `test malformed repeated test with injected RepetitionInfo for @BeforeAll method highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class WithBeforeEach {
        @org.junit.jupiter.api.BeforeAll
        void <warning descr="Method 'beforeAllWithRepetitionInfo' annotated with '@BeforeAll' should be static and not declare parameter 'repetitionInfo'">beforeAllWithRepetitionInfo</warning>(org.junit.jupiter.api.RepetitionInfo repetitionInfo) { }
      }
    """.trimIndent())
  }
  fun `test malformed repeated test with non-positive repetitions highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class WithRepeated {
        @org.junit.jupiter.api.RepeatedTest(<warning descr="The number of repetitions must be greater than zero">-1</warning>)
        void repeatedTestNegative() { }

        @org.junit.jupiter.api.RepeatedTest(<warning descr="The number of repetitions must be greater than zero">0</warning>)
        void repeatedTestBoundaryZero() { }
      }
    """.trimIndent())
  }

  /* Malformed before after */
  fun `test malformed before highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class MainTest {
        @org.junit.Before
        String <warning descr="Method 'before' annotated with '@Before' should be public, of type 'void' and not declare parameter 'i'">before</warning>(int i) { return ""; }
      }
    """.trimIndent())
  }
  fun `test malformed before each highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        String <warning descr="Method 'beforeEach' annotated with '@BeforeEach' should be of type 'void' and not declare parameter 'i'">beforeEach</warning>(int i) { return ""; }
      }
    """.trimIndent())
  }
  fun `test malformed before change signature quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class MainTest {
        @org.junit.Before
        String bef<caret>ore(int i) { return ""; }
      }
    """.trimIndent(), """
      class MainTest {
        @org.junit.Before
        public void before() { return ""; }
      }
    """.trimIndent(), "Fix 'before' method signature")
  }
  fun `test malformed before remove private quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        private void bef<caret>oreEach() { }
      }
    """.trimIndent(), """
      class MainTest {
        @org.junit.jupiter.api.BeforeEach
        public void bef<caret>oreEach() { }
      }
    """.trimIndent(), "Fix 'beforeEach' method signature")
  }
  fun `test malformed before class no highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class BeforeAllStatic {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll() { }
      }  
            
      @org.junit.jupiter.api.TestInstance(org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS)
      class BeforeAllTestInstancePerClass {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll() { }
      }
      

      class TestParameterResolver implements org.junit.jupiter.api.extension.ParameterResolver {
        @Override
        public boolean supportsParameter(
          org.junit.jupiter.api.extension.ParameterContext parameterContext, 
          org.junit.jupiter.api.extension.ExtensionContext extensionContext
        ) { return false; }

        @Override
        public Object resolveParameter(
          org.junit.jupiter.api.extension.ParameterContext parameterContext, 
          org.junit.jupiter.api.extension.ExtensionContext extensionContext
        ) { return null; }
      }

      @org.junit.jupiter.api.extension.ExtendWith(TestParameterResolver.class)
      class ParameterResolver {
        @org.junit.jupiter.api.BeforeAll
        public static void beforeAll(String foo) { }
      }
    """.trimIndent())
  }
  fun `test malformed before class highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        String <warning descr="Method 'beforeAll' annotated with '@BeforeAll' should be static, of type 'void' and not declare parameter 'i'">beforeAll</warning>(int i) { return ""; }
      }
    """.trimIndent())
  }
  fun `test malformed before all quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        String before<caret>All(int i) { return ""; }
      }
    """.trimIndent(), """
      class MainTest {
        @org.junit.jupiter.api.BeforeAll
        static void beforeAll() { return ""; }
      }
    """.trimIndent(), "Fix 'beforeAll' method signature")
  }

  /* Malformed Datapoint(s) */
  fun `test malformed dataPoint no highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint public static Object f1;
      }
    """.trimIndent())
  }
  fun `test malformed dataPoint non-static highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint public Object <warning descr="Field 'f1' annotated with '@DataPoint' should be static">f1</warning>;
      }
    """.trimIndent())
  }
  fun `test malformed dataPoint non-public highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint static Object <warning descr="Field 'f1' annotated with '@DataPoint' should be public">f1</warning>;
      }
    """.trimIndent())
  }
  fun `test malformed dataPoint field highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object <warning descr="Field 'f1' annotated with '@DataPoint' should be static and public">f1</warning>;
      }
    """.trimIndent())
  }
  fun `test malformed datapoint method highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object <warning descr="Method 'f1' annotated with '@DataPoint' should be static and public">f1</warning>() { return null; }
      }
    """.trimIndent())
  }
  fun `test malformed datapoints method highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoints Object <warning descr="Method 'f1' annotated with '@DataPoints' should be static and public">f1</warning>() { return null; }
      }
    """.trimIndent())
  }
  fun `test malformed dataPoint quickfix make method public and static`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class Test {
        @org.junit.experimental.theories.DataPoint Object f<caret>1() { return null; }
      }
    """.trimIndent(), """
      class Test {
        @org.junit.experimental.theories.DataPoint
        public static Object f1() { return null; }
      }
    """.trimIndent(), "Fix 'f1' method signature")
  }

  /* Malformed setup/teardown */
  fun `test malformed setup no highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class C extends junit.framework.TestCase {
        @Override
        public void setUp() { }
      }  
    """.trimIndent())
  }
  fun `test malformed setup highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class C extends junit.framework.TestCase {
        private void <warning descr="Method 'setUp' should be non-private, non-static, have no parameters and of type void">setUp</warning>(int i) { }
      }  
    """.trimIndent())
  }
  fun `test malformed setup quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class C extends junit.framework.TestCase {
        private void set<caret>Up(int i) { }
      }  
    """.trimIndent(), """
      class C extends junit.framework.TestCase {
        public void setUp() { }
      }  
    """.trimIndent(), "Fix 'setUp' method signature")
  }

  /* Malformed rule */
  fun `test malformed rule field non-public highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class RuleTest {
        @org.junit.Rule
        private SomeTestRule <warning descr="Field 'x' annotated with '@Rule' should be public">x</warning>;

        @org.junit.Rule
        public static SomeTestRule <warning descr="Field 'y' annotated with '@Rule' should be non-static">y</warning>;
      }
    """.trimIndent())
  }
  fun `test malformed rule field non TestRule type highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class RuleTest {
        @org.junit.Rule
        public int <warning descr="Field 'x' annotated with '@Rule' should be of type 'org.junit.rules.TestRule'">x</warning>;
      }
    """.trimIndent())
  }
  fun `test malformed rule method static highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class RuleTest {        
        @org.junit.Rule
        public static SomeTestRule <warning descr="Method 'y' annotated with '@Rule' should be non-static">y</warning>() { 
          return new SomeTestRule();  
        };        
      }
    """.trimIndent())
  }
  fun `test malformed class rule field highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        static SomeTestRule <warning descr="Field 'x' annotated with '@ClassRule' should be public">x</warning> = new SomeTestRule();

        @org.junit.ClassRule
        public SomeTestRule <warning descr="Field 'y' annotated with '@ClassRule' should be static">y</warning> = new SomeTestRule();

        @org.junit.ClassRule
        private SomeTestRule <warning descr="Field 'z' annotated with '@ClassRule' should be static and public">z</warning> = new SomeTestRule();

        @org.junit.ClassRule
        public static int <warning descr="Field 't' annotated with '@ClassRule' should be of type 'org.junit.rules.TestRule'">t</warning> = 0;
      }
    """.trimIndent())
  }
  fun `test malformed rule make field public quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class RuleQfTest {
        @org.junit.Rule
        private int x<caret>;
      }
    """.trimIndent(), """      
      class RuleQfTest {
        @org.junit.Rule
        public int x;
      }
    """.trimIndent(), "Fix 'x' field signature")
  }
  fun `test malformed rule make field non-static quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class RuleQfTest {
        @org.junit.Rule
        public static int y<caret>() { return 0; }
      }
    """.trimIndent(), """
      class RuleQfTest {
        @org.junit.Rule
        public int y() { return 0; }
      }
    """.trimIndent(), "Fix 'y' method signature")
  }
  fun `test malformed class rule make field public quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        static SomeTestRule x<caret> = new SomeTestRule();
      }
    """.trimIndent(), """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        public static SomeTestRule x = new SomeTestRule();
      }
    """.trimIndent(), "Fix 'x' field signature")
  }
  fun `test malformed class rule make field static quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        public SomeTestRule y<caret> = new SomeTestRule();
      }
    """.trimIndent(), """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        public static SomeTestRule y = new SomeTestRule();
      }
    """.trimIndent(), "Fix 'y' field signature")
  }
  fun `test malformed class rule make field public and static quickfix`() {
    myFixture.testQuickFix(ULanguage.JAVA, """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        private SomeTestRule z<caret> = new SomeTestRule();
      }
    """.trimIndent(), """
      class SomeTestRule implements org.junit.rules.TestRule {
        @org.jetbrains.annotations.NotNull
        @Override
        public org.junit.runners.model.Statement apply(
          @org.jetbrains.annotations.NotNull org.junit.runners.model.Statement base, 
          @org.jetbrains.annotations.NotNull org.junit.runner.Description description
        ) { return base; }
      }

      class ClassRuleTest {
        @org.junit.ClassRule
        public static SomeTestRule z = new SomeTestRule();
      }
    """.trimIndent(), "Fix 'z' field signature")
  }

  /* Malformed test */
  fun `test malformed test for JUnit 3 highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class JUnit3TestMethodIsPublicVoidNoArg extends junit.framework.TestCase {
        void <warning descr="Method 'testOne' should be public, non-static, have no parameters and of type void">testOne</warning>() { }
        public int <warning descr="Method 'testTwo' should be public, non-static, have no parameters and of type void">testTwo</warning>() { return 2; }
        public static void <warning descr="Method 'testThree' should be public, non-static, have no parameters and of type void">testThree</warning>() { }
        public void <warning descr="Method 'testFour' should be public, non-static, have no parameters and of type void">testFour</warning>(int i) { }
        public void testFive() { }
        void testSix(int i) { } //ignore when method doesn't look like test anymore
      }
    """.trimIndent())
  }
  fun `test malformed test for JUnit 4 highlighting`() {
    myFixture.addClass("""
      package mockit;
      public @interface Mocked { }
    """.trimIndent())
    myFixture.testHighlighting(ULanguage.JAVA, """
      class JUnit4TestMethodIsPublicVoidNoArg {
        @org.junit.Test void <warning descr="Method 'testOne' annotated with '@Test' should be public">testOne</warning>() {}
        @org.junit.Test public int <warning descr="Method 'testTwo' annotated with '@Test' should be of type 'void'">testTwo</warning>() { return 2; }
        @org.junit.Test public static void <warning descr="Method 'testThree' annotated with '@Test' should be non-static">testThree</warning>() {}
        @org.junit.Test public void <warning descr="Method 'testFour' annotated with '@Test' should not declare parameter 'i'">testFour</warning>(int i) {}
        @org.junit.Test public void testFive() {}
        @org.junit.Test public void testMock(@mockit.Mocked String s) {}
      }
    """.trimIndent())
  }
  fun `test malformed test for JUnit 4 runWith highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      @org.junit.runner.RunWith(org.junit.runner.Runner.class)
      class JUnit4RunWith {
          @org.junit.Test public int <warning descr="Method 'testMe' annotated with '@Test' should be of type 'void' and not declare parameter 'i'">testMe</warning>(int i) { return -1; }
      }
    """.trimIndent())
  }
  fun `test malformed test with parameter resolver`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.jupiter.api.extension.*;
      import org.junit.jupiter.api.Test;
      
      class MyResolver implements ParameterResolver {
        @Override
        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
          return true;
        }
           
        @Override
        public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException { 
          return null;
        }
      }
      
      @ExtendWith(MyResolver.class)
      class Foo {
        @Test
        void parametersExample(String a, String b) { }
      }
      
      @ExtendWith(MyResolver.class)
      @interface ResolverAnnotation { }
      
      @ExtendWith(MyResolver.class)
      class Bar {
        @org.junit.jupiter.api.extension.RegisterExtension
        static final MyResolver integerResolver = new MyResolver();
      
        @Test
        void parametersExample(String a, String b) { }
      }
      
      class FooBar {
        @Test
        void parametersExample(@ResolverAnnotation String a, @ResolverAnnotation String b) { }
      }
    """.trimIndent())
  }
}
