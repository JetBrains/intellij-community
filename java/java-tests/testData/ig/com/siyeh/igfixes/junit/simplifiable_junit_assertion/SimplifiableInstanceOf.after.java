// "Fix all 'Simplify assertion' problems in file" "true"
package com.siyeh.igtest.junit;

import org.testng.Assert;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimplifiableInstanceOf {
  public void test() {
    Object a = "reason:";
    My.assertTrue(a instanceof java.lang.String, "ignore" );
    Assertions.assertFalse(a instanceof java.lang.String, "ignore");
      assertInstanceOf(java.lang.String.class, a);
      assertInstanceOf(java.lang.String.class, a, "message");
      assertInstanceOf(java.lang.String.class, a, () -> a + " message");
      Child.assertInstanceOf(java.lang.String.class, a, "message");
    Child.assertTrue(a instanceof java.lang.String, "message1", "message2");
    Child.assertTrue(a instanceof java.lang.String, "message1", "message2", "message3");
    Assert.assertTrue(a instanceof java.lang.String, "ignore" );
  }

  private static class String {
  }

  private static class My  {
    public static void assertTrue(boolean condition, java.lang.String message) {
    }

    public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue, java.lang.String message) {
      return (T)actualValue;
    }
  }

  private static class Child extends Assertions {
    public static void assertTrue(boolean condition, java.lang.String message1, java.lang.String message2) {
    }

    public static void assertTrue(boolean condition, java.lang.String message1, java.lang.String message2, java.lang.String message3) {
    }

    public static <T> T assertInstanceOf(Class<T> expectedType, Object actualValue, java.lang.String message1, java.lang.String message2) {
      return (T)actualValue;
    }
  }
}
