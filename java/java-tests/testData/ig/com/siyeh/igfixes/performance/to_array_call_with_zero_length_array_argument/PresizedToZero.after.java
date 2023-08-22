package com.siyeh.igfixes.performance.to_array_call_with_zero_length_array_argument;

import java.util.List;

class IntroduceVariable {

  static List<String> someFunc()
  {
    return null;
  }

  public static void main(String... args)

  {
    String[] foo = someFunc().toArray(new String[0]);
  }
}