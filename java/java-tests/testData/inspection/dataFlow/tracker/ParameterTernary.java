/*
Value is always false (trim(b ? s : s) == ""; line#14)
  According to inferred contract, method 'trim' returns 'null' value when parameter != null (trim; line#14)
    's' is known to be 'non-null' from line #13 (s == null; line#13)
  and expression cannot be null as it's literal (""; line#14)
 */
class X {
  static String trim(String s) {
    return s == null ? "foo" : null;
  }

  void foo(boolean b, String s) {
    if (s == null) return;
    if (<selection>trim(b ? s : s) == ""</selection>) {

    }
  }
}