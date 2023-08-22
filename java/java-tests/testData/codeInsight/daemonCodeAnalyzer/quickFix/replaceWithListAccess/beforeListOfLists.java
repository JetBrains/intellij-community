// "Replace with list access" "true-preview"

import java.util.List;

class A {
  void test(List<List> lists) {
    System.out.println(li<caret>sts[0].get(0));
  }
}