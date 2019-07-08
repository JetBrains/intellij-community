// "Replace 'switch' with 'if'" "true"
class Test {
  void foo(int x) {
      if (x == 0 || x == 1) {
          throw new IllegalArgumentException();
      } else if (x == 2 || x == 3) {
          if (Math.random() > 0.5) return;
          System.out.println("two or three");
      } else if (x == 4) {
          System.out.println("four");
      }
  }
}