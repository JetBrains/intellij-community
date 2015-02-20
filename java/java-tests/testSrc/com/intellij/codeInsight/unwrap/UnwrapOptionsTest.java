package com.intellij.codeInsight.unwrap;

public class UnwrapOptionsTest extends UnwrapTestCase {
  public void testNoOptions() throws Exception {
    assertOptions("<caret>\n");
  }
}
