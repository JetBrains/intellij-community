// "Replace with list access" "true"

import java.util.ArrayList;

class A {
  void test(ArrayList<Integer> list) {
    lis<caret>t[0] = 5;
  }
}