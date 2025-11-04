package com.siyeh.igfixes.jdk.vararg_parameter;

public class JavadocReference {
  void convertIndexes(String str, int[] indexes) {
    System.out.println(indexes.length);
  }

  /**
   * Converts indexes using conversion.
   *
   * See also {@link #convertIndexes(String, int[])}
   */
  void test() {
    convertIndexes("hi!", new int[]{1, 2, 3});
  }
}