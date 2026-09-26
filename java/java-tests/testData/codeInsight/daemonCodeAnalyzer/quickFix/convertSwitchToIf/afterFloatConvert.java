// "Replace 'switch' with 'if'" "true-preview"
class Test {
  void foo(float f) {
      if (f == 1.5f) {
          System.out.println("one and half");
      } else if (f == 2.5f) {
          System.out.println("two and half");
      }
  }
}
