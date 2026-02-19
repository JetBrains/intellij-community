// "Remove redundant assignment" "true-preview"
class Foo {
  void bar(int begin) {
    int current;
    int sent = <caret>current = begin - 1;
    current = Math.abs(begin);
    System.out.println(sent);
    System.out.println(current);
  }
}