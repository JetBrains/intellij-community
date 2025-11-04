package com.siyeh.igfixes.jdk.vararg_parameter;

public class JavadocReference {
  void convertIn<caret>dexes(String str, int... indexes) {
    System.out.println(indexes.length);
  }

  /**
   * Converts indexes using conversion.
   *
   * See also {@link #convertIndexes(String, int...)}
   */
  void test() {
    convertIndexes("hi!", 1, 2, 3);
  }
}