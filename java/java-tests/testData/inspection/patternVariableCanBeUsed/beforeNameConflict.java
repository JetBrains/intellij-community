// "Replace 'n' with pattern variable" "false"
class X {
  void test(Object obj) {
    if (!(obj instanceof Number)) return;
    if (false) {
      Number n;
    }
    Number <caret>n = (Number)obj;
    System.out.println(n.longValue());
  }
}