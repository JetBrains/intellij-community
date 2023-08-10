// "Remove redundant assignment" "true-preview"
class Foo {
  int sent;

  void bar(int begin) {
    int current;
    sent = <caret>current = begin - 1;
    current = Math.abs(begin);
    System.out.println(sent);
    System.out.println(current);
  }
}