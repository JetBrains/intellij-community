// "Move 'return' closer to computation of the value of 'x'" "false"
class X {
  int test() {
    int x = 0;
    Runnable r = () -> {
      try {
        if (r == null) {
          x = 1;
        }
        return <caret>x;
      }
      finally {
        System.out.println(x);
      }
    };
  }
}