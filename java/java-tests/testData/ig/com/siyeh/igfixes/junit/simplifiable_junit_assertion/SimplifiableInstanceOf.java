// "Fix all 'Simplify assertion' problems in file" "true"
package com.siyeh.igtest.junit;

import org.testng.Assert;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SimplifiableInstanceOf {
  public void test() {
    Object a = "reason:";
    My.assertTrue(a instanceof java.lang.String, "ignore" );
    Assertions.assertFalse(a instanceof java.lang.String, "ignore");
    Assertions.<warning descr="'assertTrue()' can be simplified to 'assertInstanceOf()'">assertTrue</warning>(a instanceof java.lang.String);
    org.junit.jupiter.api.Assertions.<warning descr="'assertTrue()' can be simplified to 'assertInstanceOf()'">assertTrue</warning>(a instanceof java.lang.String, "message");
    <warning descr="'assertTrue()' can be simplified to 'assertInstanceOf()'"><caret>assertTrue</warning>(a instanceof java.lang.String, () -> a + " message");
    Child.<warning descr="'assertTrue()' can be simplified to 'assertInstanceOf()'">assertTrue</warning>(a instanceof java.lang.String, "message");
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
