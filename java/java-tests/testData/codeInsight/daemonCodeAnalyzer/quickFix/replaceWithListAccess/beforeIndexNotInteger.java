// "Replace with list access" "false"

import java.lang.Object;
import java.util.ArrayList;

class A {
  void test(ArrayList list) {
    System.out.println(lis<caret>t[new Object(3)]);
  }
}