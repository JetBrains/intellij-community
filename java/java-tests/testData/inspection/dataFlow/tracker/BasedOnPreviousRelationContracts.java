/*
Value is always false (b.equals("x"); line#15)
  According to hard-coded contract, method 'equals' returns 'false' when b != "x" (equals; line#15)
    Condition 'b != "x"' was deduced
      It's known that 'b != a' from line #12 (a.equals(b); line#12)
      and it's known that 'a == "x"' from line #14 (a.equals("x"); line#14)
 */

public class T {

  void test(String a, String b) {
    if(a.equals(b)) {

    } else if (a.equals("x")) {
      if (<selection>b.equals("x")</selection>) {}
    }
  }
}
