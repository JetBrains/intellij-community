class MethodSinceNewerJdk {
  void test(CharSequence cs) {
    if (cs instanceof String) {
      ((String)cs).getChars(0, 10, new char[10], 0);
      ((<warning descr="Casting 'cs' to 'String' is redundant">String</warning>)cs).charAt(0);
    }
  }
}