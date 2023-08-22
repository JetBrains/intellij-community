// "Replace 'switch' with 'if'" "true-preview"
class X {
  void test(int x) {
    <caret>switch (x) {
      case 1 -> { System.out.println("");}
      case 2 -> System.out.println("oops");
    }
  }
}