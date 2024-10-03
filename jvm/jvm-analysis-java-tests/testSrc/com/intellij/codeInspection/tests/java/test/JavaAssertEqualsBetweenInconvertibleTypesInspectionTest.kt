package com.intellij.codeInspection.tests.java.test

import com.intellij.jvm.analysis.internal.testFramework.test.AssertEqualsBetweenInconvertibleTypesInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.intellij.lang.annotations.Language
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@RunWith(BlockJUnit4ClassRunner::class)
class JavaAssertEqualsBetweenInconvertibleTypesInspectionTest : AssertEqualsBetweenInconvertibleTypesInspectionTestBase() {
  @Test
  fun `test JUnit 4 assertEquals`() {

    @Language("JAVA") val code = """
      import static org.junit.Assert.assertEquals;
      import org.junit.Test;
      
      import java.util.*;
      
      interface A { }
      
      interface B extends A { }
      
      class GenericClass<T> { }
      
      public class AssertEqualsBetweenInconvertibleTypesTest {
          public static boolean areEqual(Object a, GenericClass<String> b) {
              return a.equals(b);
          }    
      
          @Test
          public void test() {
              Double d = 1.0;
              assertEquals(1.0, d , 0.0); // fine.
              assertEquals(1.0, d , 0); //  'assertEquals()' between inconvertible types 'Double' and 'int'
              assertEquals(1, d , 0.0); //  Doesn't complain even though perhaps it should.
          }
      
          @Test
          public void testFoo() {
              org.junit.Assert.<warning descr="'assertEquals()' between objects of inconvertible types 'String' and 'double'">assertEquals</warning>("java", 1.0);
          }
      
          @Test
          public void testCollection() {
              Collection<A> c1 = null;
              Collection<B> c2 = null;
              assertEquals(c1, c2);
              assertEquals(new ArrayList<String>(){}, new ArrayList<String>());
              assertEquals(new TreeSet<String>(){}, new HashSet<String>());
              <warning descr="'assertEquals()' between objects of inconvertible types 'TreeSet<Integer>' and 'HashSet<String>'">assertEquals</warning>(new TreeSet<Integer>(), new HashSet<String>());
          }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code, fileName = "AssertEqualsBetweenInconvertibleTypesTest")
  }

  @Test
  fun `test JUnit 5 assertEquals`() {
    @Language("JAVA") val code = """
      import static org.junit.jupiter.api.Assertions.assertEquals;
      
      class SampleTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          <warning descr="'assertEquals()' between objects of inconvertible types 'int' and 'String'">assertEquals</warning>(1, "", "error message");
          <warning descr="'assertEquals()' between objects of inconvertible types 'int' and 'String'">assertEquals</warning>(1, "", () -> "error message in supplier");
          assertEquals(1, 42, "message");
          assertEquals(1, 42, () -> "message");
        }
      }
    """.trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ assertEquals`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      
      class SampleTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          Assertions.assertThat("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2);
          Assertions.assertThat("foo").isEqualTo("bar");
          Assertions.assertThat("foo").describedAs("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2);
          Assertions.assertThat("foo").as("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2);
        }
      }
    """.trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test JUnit 4 assertNotEquals`() {
    @Language("JAVA") val code = """
      import static org.junit.Assert.assertNotEquals;
      import org.junit.Test;
      
      import java.util.*;
      
      public class AssertNotEqualsBetweenInconvertibleTypesTest {
          @Test
          public void test() {
              <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'String' and 'int'">assertNotEquals</weak_warning>("java", 1);
              <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotEquals</weak_warning>(new int[0], 1.0);
              assertNotEquals(new int[0], new int[1]); //ok
          }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code, fileName = "AssertNotEqualsBetweenInconvertibleTypesTest")
  }

  @Test
  fun `test JUnit 5 assertNotEquals`() {
    @Language("JAVA") val code = """
      import static org.junit.jupiter.api.Assertions.assertNotEquals;
      import org.junit.jupiter.api.Test; 
      
      class SampleTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'String' and 'int'">assertNotEquals</weak_warning>("java", 1, "message");
          <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotEquals</weak_warning>(new int[0], 1.0, "message");
          assertNotEquals(new int[0], new int[1]); //ok
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ assertNotEquals`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      
      class MyTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          Assertions.assertThat("java").as("test").<weak_warning descr="Possibly redundant assertion: incompatible types are compared 'String' and 'int'">isNotEqualTo</weak_warning>(1);
          Assertions.assertThat(new int[0]).describedAs("test").<weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int[]' and 'double'">isNotEqualTo</weak_warning>(1.0);
          Assertions.assertThat(new int[0]).isNotEqualTo(new int[1]); //ok
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test JUnit 4 assertNotSame`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static org.junit.Assert.assertNotSame;
      import org.junit.Test;

      import java.util.*;

      public class AssertNotSameBetweenInconvertibleTypes {
          @Test
          public void test() {
              <warning descr="Redundant assertion: incompatible types are compared 'String' and 'int'">assertNotSame</warning>("java", 1);
              <warning descr="Redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotSame</warning>(new int[0], 1.0);
              assertNotSame(new int[0], new int[1]); //ok
          }
      }
    """.trimIndent(), fileName = "AssertNotSameBetweenInconvertibleTypes")
  }

  @Test
  fun `test JUnit 5 assertNotSame`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static org.junit.jupiter.api.Assertions.assertNotSame;

      class MyTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          <warning descr="Redundant assertion: incompatible types are compared 'String' and 'int'">assertNotSame</warning>("java", 1, "message");
          <warning descr="Redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotSame</warning>(new int[0], 1.0, "message");
          assertNotSame(new int[0], new int[1]); //ok
        }
      }
    """.trimIndent())
  }

  @Test
  fun `test AssertJ assertNotSame`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static org.assertj.core.api.Assertions.assertThat;

      class MyTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          assertThat("java").as("test").<warning descr="Redundant assertion: incompatible types are compared 'String' and 'int'">isNotSameAs</warning>(1);
          assertThat(new int[0]).describedAs("test").<warning descr="Redundant assertion: incompatible types are compared 'int[]' and 'double'">isNotSameAs</warning>(1.0);
          assertThat(new int[0]).isNotSameAs(new int[1]); //ok
        }
      }
    """.trimIndent())
  }

  @Test
  fun `test JUnit 4 assertSame`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static org.junit.Assert.assertSame;

      class MyTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          <warning descr="'assertSame()' between objects of inconvertible types 'String' and 'int'">assertSame</warning>("foo", 2);
          <warning descr="'assertSame()' between objects of inconvertible types 'int[]' and 'int'">assertSame</warning>(new int[2], 2);
          assertSame(1, 2); // ok
        }
      }
    """.trimIndent())
  }

  @Test
  fun `test JUnit 5 assertSame`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static org.junit.jupiter.api.Assertions.assertSame;

      class MyTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          <warning descr="'assertSame()' between objects of inconvertible types 'int' and 'String'">assertSame</warning>(1, "", "Foo");
          <warning descr="'assertSame()' between objects of inconvertible types 'int' and 'int[]'">assertSame</warning>(1, new int[2], () -> "Foo in supplier");
          assertSame(1, 2, "message"); // ok
          assertSame(1, 2, () -> "message"); // ok
        }
      }
    """.trimIndent())
  }

  @Test
  fun `test AssertJ assertSame`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static org.assertj.core.api.Assertions.assertThat;

      class MyTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          assertThat(1).<warning descr="'isSameAs()' between objects of inconvertible types 'int' and 'String'">isSameAs</warning>("foo");
          assertThat("foo").describedAs("foo").<warning descr="'isSameAs()' between objects of inconvertible types 'String' and 'int'">isSameAs</warning>(2);
          assertThat(new int[2]).as("array").<warning descr="'isSameAs()' between objects of inconvertible types 'int[]' and 'int'">isSameAs</warning>(2);
          assertThat(1).isSameAs(2); // ok
        }
      }
    """.trimIndent())
  }

  @Test
  fun `test AssertJ first element type match`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import java.util.List;
      
      class MySampleTest {
          @org.junit.jupiter.api.Test
          void testFirstMatch() {
              Assertions.assertThat(List.of("1"))
                .first()
                .isEqualTo("1");
          }
          
          @org.junit.jupiter.api.Test
          void testFirstNoMatch() {
              Assertions.assertThat(List.of("1"))
                .first()
                .<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(1);
          }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ last element type match`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.ArrayList;
      import java.util.List;
      
      class MyList<T> extends ArrayList<Integer> {}
      
      class MySampleTest {
          @Test
          void testNoHighlightBecauseCorrect() {
              Assertions.assertThat(List.of("1"))
                .last()
                .isEqualTo("1");
          }
          
          @Test
          void testNoHighlightBecauseCorrect_complexCase() {
            final var myList = new MyList<String>();
            myList.add(1);
            Assertions.assertThat(myList)
              .last()
              .isEqualTo(1);
          }
          
          @Test
          void testHighlightBecauseIncorrect_complexCase() {
            final var myList = new MyList<String>();
            myList.add(1);
            Assertions.assertThat(myList)
              .last()
              .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1");
          }
          
          
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ single element type match`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.assertj.core.api.Assertions;
      import java.util.List;
      
      class MyTest {
          @org.junit.jupiter.api.Test
          void testSingleElement() {
            Assertions.assertThat(List.of(1))
              .singleElement()
              .isEqualTo(1);
          }
      }
    """.trimIndent())
  }

  @Test
  fun `test AssertJ single element type mismatch`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.assertj.core.api.Assertions;
      import java.util.List;
      
      class MyTest {
          @org.junit.jupiter.api.Test
          void testSingleElement() {
            Assertions.assertThat(List.of(1))
              .singleElement()
              .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1");
          }
          
          @org.junit.jupiter.api.Test
          void testSingleElementPass() {
            Assertions.assertThat(List.of(1))
              .singleElement()
              .isEqualTo(1);
          }
      }
    """.trimIndent())
  }

  @Test
  fun `test AssertJ element type mismatch`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.assertj.core.api.Assertions;
      import java.util.List;
      
      class MyTest {
          @org.junit.jupiter.api.Test
          void testSingleElement() {
            Assertions.assertThat(List.of(1, 2, 3))
              .element(1)
              .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("2");
          }
          
          @org.junit.jupiter.api.Test
          void testSingleElementPass() {
            Assertions.assertThat(List.of(1, 2, 3))
              .element(1)
              .isEqualTo(2);
          }
      }
    """.trimIndent())
  }

  @Test
  fun `test AssertJ extracting single element lambda type match`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.List;
      
      class MySampleTest {
        @Test
        void testExtractingHighlightBecauseIncorrect() {
          Assertions.assertThat(List.of("1"))
            .extracting(elem -> Integer.valueOf(elem))
            .singleElement()
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1");
        }
        
        @Test
        void testExtractingNoHighlightBecauseCorrect() {
          Assertions.assertThat(List.of("1"))
            .extracting(elem -> Integer.valueOf(elem))
            .singleElement()
            .isEqualTo(1);
        }
        
        @Test
        void testExtractingNoHighlightBecauseTooComplex() {
          Assertions.assertThat(List.of("1"))
            .extracting((value) -> {
              if (Math.random() > 0.5) {
                return value.toString();
              } else {
                return value;
              }
            })
            .singleElement()
            .isEqualTo("1");
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ extracting single element method reference type match`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.List;
      
      class MySampleTest {
        @Test
        void testExtractingHighlightBecauseIncorrect() {
          Assertions.assertThat(List.of("1"))
            .extracting(Integer::valueOf)
            .singleElement()
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1");
        }
        
        @Test
        void testExtractingNoHighlightBecauseCorrect() {
          Assertions.assertThat(List.of("1"))
            .extracting(Integer::valueOf)
            .singleElement()
            .isEqualTo(1);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ extracting single element method reference type match - complex case`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.List;
      import java.util.function.Function;
      import java.util.function.Supplier;
      
      class MySampleTest {
        @Test
        void testExtractingHighlightBecauseIncorrect() {
          Supplier<Function<String, Integer>> giveMeFunction = () -> (String value) -> Integer.valueOf(value);
          Assertions.assertThat(List.of("1"))
            .extracting(giveMeFunction.get())
            .singleElement()
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1");
        }
                
        @Test
        void testExtractingNoHighlightBecauseCorrect() {
          Supplier<Function<String, Integer>> giveMeFunction = () -> (String value) -> Integer.valueOf(value);
          Assertions.assertThat(List.of("1"))
            .extracting(giveMeFunction.get())
            .singleElement()
            .isEqualTo(1);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ extracting method reference type match`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.function.Function;
      import java.util.function.Supplier;
      
      class MySampleTest {
        @Test
        void testExtractingHighlightBecauseIncorrect() {
          Assertions.assertThat(Integer.valueOf(1))
            .extracting(Integer::doubleValue)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Double' and 'String'">isEqualTo</warning>("1");
        }
        
        @Test
        void testExtractingNoHighlightBecauseCorrect() {
          Assertions.assertThat(Integer.valueOf(1))
            .extracting(Integer::doubleValue)
            .isEqualTo(1.0);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ extracting method reference type match - complex case`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.List;
      import java.util.function.Function;
      import java.util.function.Supplier;
      
      class Holder {
        List<String> insideValue = List.of("1");
      }
      
      class MySampleTest {
        @Test
        void testExtractingNoHighlightBecauseCorrect() {
          Assertions.assertThat(new Holder())
            .extracting("insideValue")
            .isEqualTo(List.of("1"));
        }
      
        @Test
        void testExtractingHighlightBecauseIncorrect_complexCase() {
          Supplier<Function<Integer, String>> giveMeFunction = () -> (Integer value) -> value.toString();
          Assertions.assertThat(Integer.valueOf(1))
            .extracting(giveMeFunction.get())
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(0);
        }
        
        @Test
        void testExtractingNoHighlightBecauseCorrect_complexCase() {
          Supplier<Function<Integer, String>> giveMeFunction = () -> (Integer value) -> value.toString();
          Assertions.assertThat(Integer.valueOf(1))
            .extracting(giveMeFunction.get())
            .isEqualTo("1");
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Ignore("We are aware of this edge case, but do not support it for now.")
  @Test
  fun `test AssertJ extracting method reference by string type mismatch`() {
    @Language("JAVA") val code = """
      import org.junit.jupiter.api.Test;
      import org.assertj.core.api.Assertions;
      import java.util.List;
      
      class Holder {
        List<String> insideValue = List.of("1");
      }
      
      class MySampleTest {
        @Test
        void testExtractingHighlightBecauseIncorrect() {
          Assertions.assertThat(new Holder())
            .extracting("insideValue")
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'List<String>' and 'List<Integer>'">isEqualTo</warning>(List.of(1));
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ extracting lambda type match`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      
      class MySampleTest {
        @Test
        void testExtractingHighlightBecauseIncorrect() {
          Assertions.assertThat(Integer.valueOf(1))
            .as("Mapping to String")
            .extracting(value -> value.toString())
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(1);
        }
        
        @Test
        void testExtractingNoHighlightBecauseCorrect() {
          Assertions.assertThat(Integer.valueOf(1))
            .extracting((value) -> value.toString())
            .isEqualTo("1");
        }
      
        @Test
        void testExtractingNoHighlightBecauseTooComplex() {
          Assertions.assertThat(Integer.valueOf(1))
            .extracting((value) -> {
              if (Math.random() > 0.5) {
                return value.toString();
              } else {
                return value;
              }
            })
            .isEqualTo("1");
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ exception cause does not warn if the causes match`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      
      class MySampleTest {
        @Test
        void testNoHighlightBecauseCorrect() {
          NullPointerException cause = new NullPointerException();
          IllegalArgumentException e = new IllegalArgumentException(cause);
          Assertions.assertThat(e)
            .cause()
            .isSameAs(cause);       
        }
        
        @Test
        void testHighlightBecauseIncorrect() {
          NullPointerException cause = new NullPointerException();
          IllegalArgumentException e = new IllegalArgumentException(cause);
          Assertions.assertThat(e)
            .cause()
            .<warning descr="'isSameAs()' between objects of inconvertible types 'Throwable' and 'String'">isSameAs</warning>("hello");
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Ignore("We are aware of this edge case, but do not support it for now. See IDEA-360595")
  @Test
  fun `test AssertJ assertEquals List and generic List subclass warns if types do not match`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.Optional;
      import java.util.List;
      import java.util.ArrayList;
      
      class MyList<T> extends ArrayList<Integer> { }
      
      class MySampleTest {
        @Test
        public void myTest() {
        List<String> sourceList = List.of("a", "b", "c");
        MyList<String> checkList = new MyList<>();
        checkList.add(1);
        Assertions.assertThat(sourceList)
          .<warning descr="'isEqualTo()' between objects of inconvertible types 'List<Integer>' and 'List<String>'">isEqualTo</warning>(checkList);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ assertEquals Optional without get() warns if types do not match`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.Optional;
      
      class MySampleTest {
        @Test
        public void myTest() {
          String rawStr = "hello";
          Optional<String> wrappedStr = Optional.of(rawStr);
          Assertions
            .assertThat(wrappedStr)
            .isPresent()
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Optional<String>' and 'String'">isEqualTo</warning>(rawStr);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ assertEquals Optional without get() does not warn if types match`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.Optional;
      
      class MySampleTest {
        @Test
        public void myTest() {
          String rawStr = "hello";
          Optional<String> wrappedStr = Optional.of(rawStr);
          Assertions
            .assertThat(wrappedStr)
            .isPresent()
            .isEqualTo(Optional.of("hello"));
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ assertEquals Optional with get() does not warn`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.Optional;
      import java.util.OptionalInt;
      
      class MySampleTest {
        @Test
        public void myTest_1() {
          String rawStr = "test-string";
          Optional<String> wrappedStr = Optional.of(rawStr);
          Assertions
            .assertThat(wrappedStr)
            .isPresent()
            .get()
            .isEqualTo(rawStr);
        }
        
        @Test
        public void myTest_2() {
          Assertions.assertThat(Optional.of("hello")).get().isEqualTo("hello");
          Assertions.assertThat(Optional.of("hello")).get().isNotEqualTo("bye");
            
          Assertions.assertThat(OptionalInt.of(1).getAsInt()).isNotEqualTo(2);
          Assertions.assertThat(OptionalInt.of(1).getAsInt()).isEqualTo(1);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ assertEquals List and Stream with matching generics`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.List;
      import java.util.ArrayList;
      import java.util.stream.Stream;
      import java.util.stream.IntStream;
      import java.util.stream.LongStream;
      
      class MyList<T> extends ArrayList<String> {}
      
      class MySampleTest {
        @Test
        public void testNoHighlightBecauseCorrect() {
          Stream<String> stream = Stream.of("a", "b", "c");
          List<String> list = List.of("a", "b", "c");
          Assertions
            .assertThat(stream)
            .isEqualTo(list);
        }
        
        @Test
        public void testNoHighlightBecauseCorrect_primitiveInt() {
          IntStream stream = IntStream.of(1, 2, 3);
          Assertions
            .assertThat(stream)
            .isEqualTo(List.of(1, 2, 3));
        }
        
        @Test
        public void testNoHighlightBecauseCorrect_primitiveLong() {
          LongStream stream = LongStream.of(1L, 2L, 3L);
          Assertions
            .assertThat(stream)
            .isEqualTo(List.of(1L, 2L, 3L));
        }
        
        @Test
        public void testNoHighlightBecauseCorrect_complexCase() {
          Stream<CharSequence> stream = Stream.of("a", "b", "c");
          List<String> list = List.of("a", "b", "c");
          Assertions
            .assertThat(stream)
            .isEqualTo(list);
        }
        
        @Test
        public void testNoHighlightBecauseCorrect_moreComplexCase() {
          Stream<CharSequence> stream = Stream.of("a", "b", "c");
          MyList<Integer> list = new MyList<>();
          Assertions
            .assertThat(stream)
            .isEqualTo(list);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ assertEquals List and Stream with non-matching generics`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.List;
      import java.util.ArrayList;
      import java.util.stream.Stream;
      import java.util.stream.IntStream;
      
      class MyList<T> extends ArrayList<Integer> {}
      
      class MySampleTest {
        @Test
        public void testHighlightBecauseIncorrect() {
          Stream<String> stream = Stream.of("a", "b", "c");
          List<Integer> list = List.of(1, 2, 3);
          Assertions
            .assertThat(stream)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Stream<String>' and 'List<Integer>'">isEqualTo</warning>(list);
        }
        
        @Test
        public void testHighlightBecauseIncorrect_primitives() {
          IntStream stream = IntStream.of(1, 2, 3);
          Assertions
            .assertThat(stream)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'IntStream' and 'List<String>'">isEqualTo</warning>(List.of("1", "2", "3"));
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Ignore("We are aware of this edge case, but do not support it for now. See IDEA-360595")
  @Test
  fun `test AssertJ assertEquals List and Stream with non-matching generics (more complex case)`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.List;
      import java.util.ArrayList;
      import java.util.stream.Stream;
      import java.util.stream.IntStream;
      
      class MyList<T> extends ArrayList<Integer> {}
      
      class MySampleTest {
        @Test
        public void testHighlightBecauseIncorrect_complexCase() {
          Stream<String> stream = Stream.of("a", "b", "c");
          MyList<String> list = new MyList<>();
          Assertions
            .assertThat(stream)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Stream<String>' and 'MyList<String>'">isEqualTo</warning>(list);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ assertEquals Stream and Stream with matching generics`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.stream.Stream;
      
      class MySampleTest {
        @Test
        public void myTest_1() {
          Stream<String> stream1 = Stream.of("a", "b", "c");
          Stream<String> stream2 = Stream.of("a", "b", "c");
          Assertions
            .assertThat(stream1)
            .isEqualTo(stream2);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ assertEquals Stream and Stream with non-matching generics`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      import java.util.stream.Stream;
      
      class MySampleTest {
        @Test
        public void myTest_1() {
          Stream<String> stream1 = Stream.of("a", "b", "c");
          Stream<Integer> stream2 = Stream.of(1, 2, 3);
          Assertions
            .assertThat(stream1)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'Stream<String>' and 'Stream<Integer>'">isEqualTo</warning>(stream2);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `test AssertJ using recursive comparison does not warn`() {
    @Language("JAVA") val code = """
      import org.assertj.core.api.Assertions;
      import org.junit.jupiter.api.Test;
      
      class RecursiveAssertJIntelliJIssueTest {
        @Test
        void recursiveComparison() {
          class A { int value; }
          class B { int value; }
      
          A actual = new A();
          actual.value = 1;
          
          B expected = new B();
          expected.value = 1;
          
          Assertions.assertThat(actual)
            .usingRecursiveComparison()
            .isEqualTo(expected);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `additional AssertJ smoke test 1`() {
    @Language("JAVA") val code = """
      import org.junit.jupiter.api.Test;
      import org.assertj.core.api.Assertions;
      import org.assertj.core.api.InstanceOfAssertFactories;
      import java.util.List;
      import java.math.BigInteger;
      
      class MySampleTest {
        @Test
        public void myTest() {
          List<Integer> sourceList = List.of(1, 2, 3);
          Assertions.assertThat(sourceList)
            .element(1, InstanceOfAssertFactories.DOUBLE)
            .<warning descr="'isEqualTo()' between objects of inconvertible types 'double' and 'BigInteger'">isEqualTo</warning>(BigInteger.ONE);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `additional AssertJ smoke test 2`() {
    @Language("JAVA") val code = """
      import org.junit.jupiter.api.Test;
      import org.assertj.core.api.Assertions;
      
      class MySampleTest {
        @Test
        public void myTest() {
          UnsupportedOperationException uoe = new UnsupportedOperationException();
          Assertions.assertThat(new IllegalArgumentException("Hello", uoe))
            .rootCause()
            .isEqualTo(uoe);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `additional AssertJ smoke test 3`() {
    @Language("JAVA") val code = """
      import org.junit.jupiter.api.Test;
      import org.assertj.core.api.Assertions;
      import java.util.List;
      
      class MySampleTest {
        @Test
        public void myTest() {
          Assertions.assertThat(List.of(1, 2, 3))
            .flatMap(e -> List.of(String.valueOf(e)))
            .isEqualTo(List.of("1", "2", "3"));
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `additional AssertJ smoke test 4`() {
    @Language("JAVA") val code = """
      import org.junit.jupiter.api.Test;
      import org.assertj.core.api.Assertions;
      import java.io.File;
      import java.io.IOException;
      import java.nio.file.Files;
      import java.nio.file.Path;
      
      class MySampleTest {
        @Test
        public void myTest() throws IOException {
          byte[] data = {1, 2, 3};
          Files.write(Path.of("test.dat"), data);
          File file = new File("test.dat");
          Assertions.assertThat(file)
            .binaryContent()
            .isEqualTo(data);
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }

  @Test
  fun `additional AssertJ smoke test 5`() {
    @Language("JAVA") val code = """
      import org.junit.jupiter.api.Test;
      import org.assertj.core.api.Assertions;
      import java.util.Map;
      
      class MySampleTest {
        @Test
        public void myTest() {
          Map<Integer, String> map = Map.of(1, "a", 2, "b");
          Assertions.assertThat(map)
            .extractingByKey(1)
            .isEqualTo("a");
        }
      }""".trimIndent()

    myFixture.testHighlighting(JvmLanguage.JAVA, code)
  }
}
