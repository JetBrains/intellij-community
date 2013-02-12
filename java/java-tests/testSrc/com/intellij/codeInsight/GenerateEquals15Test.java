package com.intellij.codeInsight;

/**
 * @author dsl
 */
public class GenerateEquals15Test extends GenerateEqualsTestCase {
  public void testArraysFromJava15() throws Exception {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }
}