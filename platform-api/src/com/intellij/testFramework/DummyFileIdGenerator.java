/*
 * @author max
 */
package com.intellij.testFramework;

public class DummyFileIdGenerator {
  private static int ourId = Integer.MAX_VALUE / 2;

  private DummyFileIdGenerator() {}

  public static int next() {
    return ourId++;
  }
}