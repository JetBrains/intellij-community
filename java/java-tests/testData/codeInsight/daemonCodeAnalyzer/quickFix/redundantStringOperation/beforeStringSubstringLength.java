// "Fix all 'Redundant String operation' problems in file" "true"
class Foo {
  void test(String s) {
    String s1 = s.substring(1, s.leng<caret>th());
    String s2 = s.substring(2, s1.length());
  }
}