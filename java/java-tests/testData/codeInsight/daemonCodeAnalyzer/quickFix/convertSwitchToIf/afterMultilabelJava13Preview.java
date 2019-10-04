// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(int x) {
      if (x == 0 || x == 1) {
          System.out.println("ready");
          System.out.println("steady");
      } else if (x == 2 || x == 3) {
          System.out.println("steady");
      }
      System.out.println("go");
  }
}