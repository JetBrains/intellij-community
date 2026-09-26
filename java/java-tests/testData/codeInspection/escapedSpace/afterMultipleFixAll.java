// "Fix all 'Non-terminal use of '\s' escape sequence' problems in file" "true"
class X {
  void test(String str) {
    if (str.matches(" a b c d")) {

    }
  }
}