// "Fix all 'Redundant 'String' operation' problems in file" "true"
class Foo {
  void test(String s) {
    String s1 = s.substring<caret>(s.length());
    String s2 = s.substring(s1.length());
  }
}