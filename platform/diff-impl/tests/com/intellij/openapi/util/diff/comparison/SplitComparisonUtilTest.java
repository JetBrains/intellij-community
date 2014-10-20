package com.intellij.openapi.util.diff.comparison;

// TODO
public class SplitComparisonUtilTest extends ComparisonUtilTestBase {
  public void testSplitter() {
    TestData.split("x", "z")._Def_(mod(0, 0, 1, 1)).all();
    TestData.split("x_y", "a_b")._Def_(mod(0, 0, 2, 2)).all();
    TestData.split("x_y", "a_b y")._Def_(mod(0, 0, 1, 1), mod(1, 1, 1, 1)).all();

    TestData.split("a_x_b_", " x_")._Def_(del(0, 0, 1), mod(1, 0, 1, 1), del(2, 1, 1)).def();
    TestData.split("a_x_b_", "!x_")._Def_(del(0, 0, 1), mod(1, 0, 1, 1), del(2, 1, 1)).all();
  }
}
