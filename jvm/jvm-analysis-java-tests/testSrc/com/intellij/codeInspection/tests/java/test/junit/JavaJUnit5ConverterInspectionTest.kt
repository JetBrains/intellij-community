package com.intellij.codeInspection.tests.java.test.junit

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.test.junit.JUnit5ConverterInspectionTestBase

class JavaJUnit5ConverterInspectionTest : JUnit5ConverterInspectionTestBase() {
  fun `test qualified conversion`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      import org.junit.Test;
      import org.junit.Before;
      import org.junit.Assert;

      import java.util.*;

      public class Qual<caret>ified {
        @Before
        public void setUp() {}
        
        @Test
        public void testMethodCall() throws Exception {
          Assert.assertArrayEquals(new Object[] {}, null);
          Assert.assertArrayEquals("message", new Object[] {}, null);
          Assert.assertEquals("Expected", "actual");
          Assert.assertEquals("message", "Expected", "actual");
          Assert.fail();
          Assert.fail("");
        }

        @Test
        public void testMethodRef() {
          List<Boolean> booleanList = new ArrayList<>();
          booleanList.add(true);
          booleanList.forEach(Assert::assertTrue);
        }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Assertions;
      import org.junit.jupiter.api.Test;
      import org.junit.jupiter.api.BeforeEach;
      
      import java.util.*;

      public class Qualified {
        @BeforeEach
        public void setUp() {}
        
        @Test
        public void testMethodCall() throws Exception {
          Assertions.assertArrayEquals(new Object[] {}, null);
          Assertions.assertArrayEquals(new Object[] {}, null, "message");
          Assertions.assertEquals("Expected", "actual");
          Assertions.assertEquals("Expected", "actual", "message");
          Assertions.fail();
          Assertions.fail("");
        }

        @Test
        public void testMethodRef() {
          List<Boolean> booleanList = new ArrayList<>();
          booleanList.add(true);
          booleanList.forEach(Assertions::assertTrue);
        }
      }
    """.trimIndent(), "Migrate to JUnit 5")
  }

  fun `test unqualified conversion`() {
    myFixture.testQuickFix(JvmLanguage.JAVA, """
      import org.junit.Test;
      import static org.junit.Assert.*;
      import org.junit.Assert;
      import java.util.*;

      public class UnQual<caret>ified {
        @Test
        public void testMethodCall() throws Exception {
          assertArrayEquals(new Object[] {}, null);
          assertArrayEquals("message", new Object[] {}, null);
          assertEquals("Expected", "actual");
          assertEquals("message", "Expected", "actual");
          fail();
          fail("");
        }

        @Test
        public void testMethodRef() {
          List<Boolean> booleanList = new ArrayList<>();
          booleanList.add(true);
          booleanList.forEach(Assert::assertTrue);
        }
      }
    """.trimIndent(), """
      import org.junit.jupiter.api.Assertions;
      import org.junit.jupiter.api.Test;

      import java.util.*;

      public class UnQualified {
        @Test
        public void testMethodCall() throws Exception {
          Assertions.assertArrayEquals(new Object[] {}, null);
          Assertions.assertArrayEquals(new Object[] {}, null, "message");
          Assertions.assertEquals("Expected", "actual");
          Assertions.assertEquals("Expected", "actual", "message");
          Assertions.fail();
          Assertions.fail("");
        }

        @Test
        public void testMethodRef() {
          List<Boolean> booleanList = new ArrayList<>();
          booleanList.add(true);
          booleanList.forEach(Assertions::assertTrue);
        }
      }
    """.trimIndent(), "Migrate to JUnit 5")
  }

  fun `test expected on test annotation`() {
    myFixture.testQuickFixUnavailable(JvmLanguage.JAVA, """
      import org.junit.Test;

      import static org.junit.Assert.*;

      public class Simp<caret>le {
        @Test(expected = Exception.class)
        public void testFirst() throws Exception { }
      }
    """.trimIndent())
  }
}