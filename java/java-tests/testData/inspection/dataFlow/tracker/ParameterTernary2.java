/*
Value is always false (trim(b ? s : s2) == ""; line#17)
  According to inferred contract, method 'trim' returns 'null' value when parameter != null (trim; line#17)
    One of the following happens:
      's' is known to be 'non-null' from line #15 (s == null; line#15)
      or 's2' is known to be 'non-null' from line #16 (s2 == null; line#16)
  and expression cannot be null as it's literal (""; line#17)
 */
class X {
  static String trim(String s) {
    return s == null ? "foo" : null;
  }

  void foo(boolean b, String s, String s2) {
    if (s == null) return;
    if (s2 == null) return;
    if (<selection>trim(b ? s : s2) == ""</selection>) {

    }
  }
}