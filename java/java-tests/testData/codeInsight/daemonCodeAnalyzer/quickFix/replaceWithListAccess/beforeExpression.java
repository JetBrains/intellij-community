// "Replace with list access" "true"

import java.lang.Math;
import java.util.List;

class A {
  void test(List<List> lists) {
    System.out.println(lis<caret>ts[Math.max(Math.abs(-2),3)]);
  }
}