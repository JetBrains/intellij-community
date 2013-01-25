public class Test extends Super {
    public static void main(String[] args) {
        args = args;
        int j = ((j) = 1);
        args = (args) = null;
    }

    private final int z = this.z;
    public static int y = ABCV.y;
    public static final int x = ABCV.x;

    static void foo() {
        y = Test.y;
    }

    void call() {
      h = super.h;
      h = (h) = 1;
    }
}
class Super {
    int h;
    int bar;
}


class Outer extends Super {
  class Inner extends Super {
    void f() {
      this.bar = Outer.this.bar;
      bar = Outer.this.bar;
      this.bar = Outer.super.bar;
      bar = Outer.super.bar;
      super.bar = Outer.super.bar;
    }
  }
}