/*
Value is always true (sb.append("foo") == sb; line#8)
  According to inferred contract, method 'append' always returns 'this' value (append; line#8)
 */
class X {
  void test() {
    StringBuilder sb = new StringBuilder();
    if (<selection>sb.append("foo") == sb</selection>) {}
  }
}