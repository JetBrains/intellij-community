// "Replace with list access" "true-preview"

import java.lang.Integer;
import java.util.ArrayList;

class A {
  void test(ArrayList list) {
    System.out.println(list.get(new Integer(0)));
  }
}