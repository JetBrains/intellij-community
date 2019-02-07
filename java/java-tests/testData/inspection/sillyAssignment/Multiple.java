public class Test extends Super {
    public static void main(String[] args) {
        args = <warning descr="Variable 'args' is assigned to itself">args</warning>;
        int j = ((<warning descr="Variable 'j' is initialized with self assignment">j</warning>) = 1);
        args = (<warning descr="Variable 'args' is assigned to itself">args</warning>) = null;
    }

    private final int z = <warning descr="Variable 'z' is initialized with self assignment">this.z</warning>;
    public static int y = <error descr="Cannot resolve symbol 'ABCV'">ABCV</error>.y;
    public static final int x = <error descr="Cannot resolve symbol 'ABCV'">ABCV</error>.x;

    static void foo() {
        y = <warning descr="Variable 'y' is assigned to itself">Test.y</warning>;
    }

    void call() {
      h = <warning descr="Variable 'h' is assigned to itself">super.h</warning>;
      h = (<warning descr="Variable 'h' is assigned to itself">h</warning>) = 1;
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