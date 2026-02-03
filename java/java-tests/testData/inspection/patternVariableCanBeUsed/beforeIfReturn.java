// "Replace 'n' with pattern variable" "true"
class X {
  void test(Object obj) {
    if (!(obj instanceof Number)) return;
    Number <caret>n = (Number)obj;
    System.out.println(n.longValue());
  }
}