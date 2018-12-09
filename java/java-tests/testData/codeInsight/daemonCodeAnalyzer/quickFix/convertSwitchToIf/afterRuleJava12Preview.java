// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(int x) {
      if (x == 0) {
          System.out.println(x);
      } else if (x == 1) {
          System.out.println("one");
      }
  }
}