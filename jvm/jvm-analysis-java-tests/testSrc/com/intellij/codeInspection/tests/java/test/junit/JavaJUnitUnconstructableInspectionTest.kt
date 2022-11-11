package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.ULanguage
import com.intellij.codeInspection.tests.test.junit.JUnitUnconstructableTestCaseInspectionTestBase

class JavaJUnitUnconstructableInspectionTest : JUnitUnconstructableTestCaseInspectionTestBase() {
  fun testPlain() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      class Plain { }
    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCase1() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import junit.framework.TestCase;

      public class <warning descr="Test class 'UnconstructableJUnit3TestCase1' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableJUnit3TestCase1</warning> extends TestCase {
          private UnconstructableJUnit3TestCase1() {
              System.out.println("");
          }
      }

    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCase2() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import junit.framework.TestCase;

      public class <warning descr="Test class 'UnconstructableJUnit3TestCase2' is not constructable because it does not have a 'public' no-arg or single 'String' parameter constructor">UnconstructableJUnit3TestCase2</warning> extends TestCase {
          public UnconstructableJUnit3TestCase2(Object foo) {
              System.out.println("");
          }
      }

    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCase3() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import junit.framework.TestCase;

      public class UnconstructableJUnit3TestCase3 extends TestCase {
          public UnconstructableJUnit3TestCase3() {
              System.out.println("");
          }
      }

    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCase4() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import junit.framework.TestCase;

      public class UnconstructableJUnit3TestCase4 extends TestCase {
          public UnconstructableJUnit3TestCase4(String foo) {
              System.out.println("");
          }
      }
    """.trimIndent())
  }

  fun testUnconstructableJUnit3TestCaseLocalClass() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import junit.framework.TestCase;

      public class UnconstructableJUnit3TestCaseLocalClass {
          public static void main() {
            class LocalClass extends TestCase { }
          }
      }
    """.trimIndent())
  }

  fun testUnconstructableJUnit4TestCase1() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.Test;
      
      public class <warning descr="Test class 'UnconstructableJUnit4TestCase1' is not constructable because it should have exactly one 'public' no-arg constructor">UnconstructableJUnit4TestCase1</warning> {
        public UnconstructableJUnit4TestCase1(String s) {}
        
        public UnconstructableJUnit4TestCase1() {}
      
        @Test
        void testMe() {}
      }
    """.trimIndent())
  }

  fun testUnconstructableJUnit4TestCase2() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.Test;

      public class UnconstructableJUnit4TestCase2 {
      	public UnconstructableJUnit4TestCase2() {
      		this("two", 1);
      	}

      	private UnconstructableJUnit4TestCase2(String one, int two) {
      		// do nothing with the parameters
      	}

      	@Test
      	public void testAssertion() {
      	}
      }
    """.trimIndent())
  }

  fun testUnconstructableJUnit4TestCase3() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import org.junit.Test;

      class <warning descr="Test class 'UnconstructableJUnit4TestCase3' is not constructable because it is not 'public'">UnconstructableJUnit4TestCase3</warning> {
        UnconstructableJUnit4TestCase3() {}

        @Test
        void testMe() {}
      }
    """.trimIndent())
  }

  fun testConstructableJunit3WithJunit4runner() {
    myFixture.testHighlighting(ULanguage.JAVA, """
      import java.util.Collection;
      import java.util.Arrays;
      import junit.framework.TestCase;
      import org.junit.runner.RunWith;
      import org.junit.runners.Parameterized;
      import org.junit.Test;

      @RunWith(Parameterized.class)
      class ConstructableJunit3WithJunit4runner extends TestCase {
        ConstructableJunit3WithJunit4runner(Integer i) {}
        
        @Parameterized.Parameters
        public static Collection<Integer> params() {
          return Arrays.asList(1, 2, 3);
        }

        @Test
        void testMe() {}
      }
    """.trimIndent())
  }
}