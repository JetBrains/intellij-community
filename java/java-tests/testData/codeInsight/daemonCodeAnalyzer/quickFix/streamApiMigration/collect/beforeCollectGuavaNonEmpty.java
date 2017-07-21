// "Replace with collect" "false"
package com.google.common.collect;

import java.util.*;

class Lists {
  public static <E> ArrayList<E> newArrayList() {
    return new ArrayList<E>();
  }
}

public class Test {
  public void test(List<String> input) {
    List<String> list = Lists.newArrayList(input);
    for(int <caret>i=0; i<10; i++) {
      if(i%2 == 0) {
        list.add(String.valueOf(i));
      }
    }
    System.out.println(list);
  }
}
