// "Replace with list access" "true"

import java.util.ArrayList;

class A {
  void test(ArrayList<Integer> list) {
    list.set(0, 5);
  }
}