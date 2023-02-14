// "Replace with list access" "true-preview"

import java.util.ArrayList;

class A {
  void test(ArrayList<Integer> list) {
    list.set(0, 5);
  }
}