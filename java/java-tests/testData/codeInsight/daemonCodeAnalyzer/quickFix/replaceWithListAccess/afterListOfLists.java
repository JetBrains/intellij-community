// "Replace with list access" "true"

import java.util.List;

class A {
  void test(List<List> lists) {
    System.out.println(lists.get(0).get(0));
  }
}