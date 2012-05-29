// "Replace with list access" "true"

import java.util.ArrayList;

class A {
  void test(ArrayList list) {
    System.out.println(lis<caret>t[0]);
  }
}