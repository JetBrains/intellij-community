// "Replace 'switch' with 'if'" "true-preview"
class Test {
  void foo(int x) {
    switch<caret> (x) {
      case 0 -> System.out.println(x);
      case 1 -> System.out.println("one");
    }
  }
}