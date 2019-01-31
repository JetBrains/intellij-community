// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(int x) {
    switch<caret> (x) {
      case 0 -> System.out.println(x);
      case 1 -> System.out.println("one");
    }
  }
}