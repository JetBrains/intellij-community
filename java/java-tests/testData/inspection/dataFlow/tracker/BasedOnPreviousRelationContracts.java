/*
Value is always false (b.equals("x"); line#15)
  According to hard-coded contract, method 'equals' returns 'false' value when this != parameter (equals; line#15)
    Condition 'b != "x"' was deduced
      Result of 'b != a' is known from line #12 (a.equals(b); line#12)
      and result of 'a == "x"' is known from line #14 (a.equals("x"); line#14)
 */

public class T {

  void test(String a, String b) {
    if(a.equals(b)) {

    } else if (a.equals("x")) {
      if (<selection>b.equals("x")</selection>) {}
    }
  }
}
