package com.intellij.codeInspection.tests.java.test

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.HamcrestAssertionsConverterInspectionTestBase

class JavaHamcrestAssertionsConverterInspectionTest : HamcrestAssertionsConverterInspectionTestBase() {
  fun `test highlighting`() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.Assert;
      import java.util.Collection;

      class Foo {
        void m() {
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 != 3);
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 == 3);
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 > 3);
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 < 3);
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 >= 3);
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(2 <= 3);

          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 != 3);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 == 3);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 > 3);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 < 3);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 >= 3);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(2 <= 3);
        }

        void m2() {
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd".equals("zxc"));
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd" == "zxc");
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>("asd".contains("qwe"));
        }

        void m3(Collection<String> c, String o) {
          Assert.<warning descr="Assert expression 'assertTrue' can be replaced with 'assertThat()' call">assertTrue</warning>(c.contains(o));
          Assert.<warning descr="Assert expression 'assertEquals' can be replaced with 'assertThat()' call">assertEquals</warning>(c, o);
          Assert.<warning descr="Assert expression 'assertEquals' can be replaced with 'assertThat()' call">assertEquals</warning>("msg", c, o);
          Assert.<warning descr="Assert expression 'assertNotNull' can be replaced with 'assertThat()' call">assertNotNull</warning>(c);
          Assert.<warning descr="Assert expression 'assertNull' can be replaced with 'assertThat()' call">assertNull</warning>(c);
          Assert.<warning descr="Assert expression 'assertFalse' can be replaced with 'assertThat()' call">assertFalse</warning>(c.contains(o));
        }

        void m(int[] a, int[] b) {
          Assert.<warning descr="Assert expression 'assertArrayEquals' can be replaced with 'assertThat()' call">assertArrayEquals</warning>(a, b);
        }
      }      
    """.trimIndent())
  }

  fun `test quickfix binary expression`() {
    myFixture.testAllQuickfixes(ULanguage.JAVA, """
      import org.junit.Assert;

      class MigrationTest {
        void migrate() {
          Assert.assertTrue(2 != 3);
          Assert.assertTrue(2 == 3);
          Assert.assertTrue(2 > 3);
          Assert.assertTrue(2 < 3);
          Assert.assertTrue(2 >= 3);
          Assert.assertTrue(2 <= 3);
          Assert.assertFalse(2 != 3);
          Assert.assertFalse(2 == 3);
          Assert.assertFalse(2 > 3);
          Assert.assertFalse(2 < 3);
          Assert.assertFalse(2 >= 3);
          Assert.assertFalse(2 <= 3);
        }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert;
      import org.hamcrest.Matchers;
      import org.junit.Assert;

      class MigrationTest {
        void migrate() {
          MatcherAssert.assertThat(2, Matchers.not(Matchers.is(3)));
          MatcherAssert.assertThat(2, Matchers.is(3));
          MatcherAssert.assertThat(2, Matchers.greaterThan(3));
          MatcherAssert.assertThat(2, Matchers.lessThan(3));
          MatcherAssert.assertThat(2, Matchers.greaterThanOrEqualTo(3));
          MatcherAssert.assertThat(2, Matchers.lessThanOrEqualTo(3));
          MatcherAssert.assertThat(2, Matchers.is(3));
          MatcherAssert.assertThat(2, Matchers.not(Matchers.is(3)));
          MatcherAssert.assertThat(2, Matchers.lessThanOrEqualTo(3));
          MatcherAssert.assertThat(2, Matchers.greaterThanOrEqualTo(3));
          MatcherAssert.assertThat(2, Matchers.lessThan(3));
          MatcherAssert.assertThat(2, Matchers.greaterThan(3));
        }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix string`() {
    myFixture.testAllQuickfixes(ULanguage.JAVA, """
      import org.junit.Assert;

      class Foo {
        void migrate() {
          Assert.assertTrue("asd".equals("zxc"));
          Assert.assertTrue("asd" == "zxc");
          Assert.assertTrue("asd".contains("qwe"));
        }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert;
      import org.hamcrest.Matchers;
      import org.junit.Assert;

      class Foo {
        void migrate() {
          MatcherAssert.assertThat("asd", Matchers.is("zxc"));
          MatcherAssert.assertThat("asd", Matchers.sameInstance("zxc"));
          MatcherAssert.assertThat("asd", Matchers.containsString("qwe"));
        }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix collection`() {
    myFixture.testAllQuickfixes(ULanguage.JAVA, """
      import org.junit.Assert;
      import java.util.Collection;

      class Foo {
        void migrate(Collection<String> c, String o) {
          Assert.assertTrue(c.contains(o));
          Assert.assertEquals(c, o);
          Assert.assertEquals("msg", c, o);
          Assert.assertNotNull(c);
          Assert.assertNull(c);
          Assert.assertFalse(c.contains(o));
        }
      }      
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert;
      import org.hamcrest.Matchers;
      import org.junit.Assert;
      import java.util.Collection;

      class Foo {
        void migrate(Collection<String> c, String o) {
          MatcherAssert.assertThat(c, Matchers.hasItem(o));
          MatcherAssert.assertThat(o, Matchers.is(c));
          MatcherAssert.assertThat("msg", o, Matchers.is(c));
          MatcherAssert.assertThat(c, Matchers.notNullValue());
          MatcherAssert.assertThat(c, Matchers.nullValue());
          MatcherAssert.assertThat(c, Matchers.not(Matchers.hasItem(o)));
        }
      }      
    """.trimIndent(), "Replace with 'assertThat()'")
  }

  fun `test quickfix array`() {
    myFixture.testAllQuickfixes(ULanguage.JAVA, """
      import org.junit.Assert;

      class Foo {
        void migrate(int[] a, int[] b) {
          Assert.assertArrayEquals(a, b);
        }
      }
    """.trimIndent(), """
      import org.hamcrest.MatcherAssert;
      import org.hamcrest.Matchers;
      import org.junit.Assert;

      class Foo {
        void migrate(int[] a, int[] b) {
          MatcherAssert.assertThat(b, Matchers.is(a));
        }
      }
    """.trimIndent(), "Replace with 'assertThat()'")
  }
}