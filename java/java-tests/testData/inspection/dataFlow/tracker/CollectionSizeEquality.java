/*
Value is always false (l1 == l2; line#16)
  Values cannot be equal because l1.size != l2.size
    Left operand is 0 (l1; line#16)
      Range is known from line #14 (!l1.isEmpty(); line#14)
    and right operand is in {3..Integer.MAX_VALUE} (l2; line#16)
      Range is known from line #15 (l2.size() < 3; line#15)
 */

import java.util.List;

class Test {
  void test(List<String> l1, List<String> l2) {
    if (!l1.isEmpty()) return;
    if (l2.size() < 3) return;
    if (<selection>l1 == l2</selection>) {
      
    }
  }
}