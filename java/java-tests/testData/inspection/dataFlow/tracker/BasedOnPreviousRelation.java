/*
Value is always false (b == c; line#14)
  Condition 'b != c' was deduced
    Condition 'b != a' was checked before (a == b; line#11)
    and condition 'a == c' was checked before (c == a; line#13)
 */

public class T {

  void test(int a, int b, int c) {
    if(a == b) {

    } else if (c == a) {
      if (<selection>b == c</selection>) {}
    }
  }
}
