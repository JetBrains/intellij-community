// "Replace 'switch' with 'if'" "true-preview"
class X {
  void test(int i) {
      if (i == 1) {//foo
          if (Math.random() > 0.5) {
              System.out.println("Hello");
          }
      }
    }

}