package com.intellij.codeInspection.tests.java.test

import com.intellij.jvm.analysis.internal.testFramework.test.AssertEqualsBetweenInconvertibleTypesInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaAssertEqualsBetweenInconvertibleTypesInspectionTest : AssertEqualsBetweenInconvertibleTypesInspectionTestBase() {
  fun `test JUnit 4 assertEquals`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static org.junit.Assert.assertEquals;
      import org.junit.Test;

      import java.util.*;
      
      interface A { }
      
      interface B extends A { }

      class GenericClass<T> { }

      public class AssertEqualsBetweenInconvertibleTypes {
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
      }
    """.trimIndent(), fileName = "AssertEqualsBetweenInconvertibleTypes")
  }

  fun `test JUnit 5 assertEquals`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static org.junit.jupiter.api.Assertions.assertEquals;

      class MyTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          <warning descr="'assertEquals()' between objects of inconvertible types 'int' and 'String'">assertEquals</warning>(1, "", "error message");
          <warning descr="'assertEquals()' between objects of inconvertible types 'int' and 'String'">assertEquals</warning>(1, "", () -> "error message in supplier");
          assertEquals(1, 42, "message");
          assertEquals(1, 42, () -> "message");
        }
      }
    """.trimIndent())
  }

  fun `test AssertJ assertEquals`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.assertj.core.api.Assertions;

      class MyTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          Assertions.assertThat("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2);
          Assertions.assertThat("foo").isEqualTo("bar");
          Assertions.assertThat("foo").describedAs("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2);
          Assertions.assertThat("foo").as("foo").<warning descr="'isEqualTo()' between objects of inconvertible types 'String' and 'int'">isEqualTo</warning>(2);
        }
      }
    """.trimIndent())
  }

  fun `test JUnit 4 assertNotEquals`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static org.junit.Assert.assertNotEquals;
      import org.junit.Test;

      import java.util.*;

      public class AssertNotEqualsBetweenInconvertibleTypes {
          @Test
          public void test() {
              <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'String' and 'int'">assertNotEquals</weak_warning>("java", 1);
              <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotEquals</weak_warning>(new int[0], 1.0);
              assertNotEquals(new int[0], new int[1]); //ok
          }
      }
    """.trimIndent(), fileName = "AssertNotEqualsBetweenInconvertibleTypes")
  }

  fun `test JUnit 5 assertNotEquals`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import static org.junit.jupiter.api.Assertions.assertNotEquals;

      class MyTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'String' and 'int'">assertNotEquals</weak_warning>("java", 1, "message");
          <weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int[]' and 'double'">assertNotEquals</weak_warning>(new int[0], 1.0, "message");
          assertNotEquals(new int[0], new int[1]); //ok
        }
      }
    """.trimIndent())
  }

  fun `test AssertJ assertNotEquals`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.assertj.core.api.Assertions;

      class MyTest {
        @org.junit.jupiter.api.Test
        void myTest() {
          Assertions.assertThat("java").as("test").<weak_warning descr="Possibly redundant assertion: incompatible types are compared 'String' and 'int'">isNotEqualTo</weak_warning>(1);
          Assertions.assertThat(new int[0]).describedAs("test").<weak_warning descr="Possibly redundant assertion: incompatible types are compared 'int[]' and 'double'">isNotEqualTo</weak_warning>(1.0);
          Assertions.assertThat(new int[0]).isNotEqualTo(new int[1]); //ok
        }
      }
    """.trimIndent())
  }

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

  fun `test Assertj first element type match`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
        import org.assertj.core.api.Assertions;
        import java.util.List;
      
        class MyTest {
            @org.junit.jupiter.api.Test
            void testExtractingNoHighlight() {
                Assertions.assertThat(List.of("1"))
                  .first()
                  .isEqualTo("1");
            }
        }    
    """.trimIndent())
  }

  fun `test Assertj single element type match`() {
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

  fun `test Assertj single element type mismatch`() {
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
      }
    """.trimIndent())
  }

  fun `_test AssertJ extracting single element type mismatch`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.assertj.core.api.Assertions;
      import java.util.List;
      
      class MyTest {
          @org.junit.jupiter.api.Test
          void testSingleElement() {
            Assertions.assertThat(List.of("1"))
              .extracting(elem -> Integer.valueOf(elem))
              .singleElement()
              .<warning descr="'isEqualTo()' between objects of inconvertible types 'Integer' and 'String'">isEqualTo</warning>("1");
          }
      }
    """.trimIndent())
  }

  fun `test Assertj extracting method reference type match`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
        import org.assertj.core.api.Assertions;
      
        class MyTest {
            @org.junit.jupiter.api.Test
            void testExtractingNoHighlight() {
                Assertions.assertThat(Integer.valueOf(1))
                        .as("Mapping to String")
                        .extracting(Object::toString)
                        .isEqualTo("1");
            }
        }    
    """.trimIndent())
  }

  fun `test Assertj extracting lambda type match`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
        import org.assertj.core.api.Assertions;
      
        class MyTest {
            @org.junit.jupiter.api.Test
            void testExtractingNoHighlight() {
                Assertions.assertThat(Integer.valueOf(1))
                        .as("Mapping to String")
                        .extracting((value) -> value.toString())
                        .isEqualTo("1");
            }
        }    
    """.trimIndent())
  }

  fun `test Assertj cause match`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
        import org.assertj.core.api.Assertions;
      
        class MyTest {
            @org.junit.jupiter.api.Test
            void testExtractingNoHighlight() {
                NullPointerException cause = new NullPointerException();
                IllegalArgumentException e = new IllegalArgumentException(cause);
                Assertions.assertThat(e).cause().isSameAs(cause);
            }
        }    
    """.trimIndent())
  }
}
