// "Replace 'obj' with pattern variable" "false"
class X {
  void test(String s) {
    if (s instanceof Object) {
      Object <caret>obj = (Object) s;
      System.out.println(obj);
    }
  }
}