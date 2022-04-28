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

    void call(int[] arr) {
      h = <warning descr="Variable 'h' is assigned to itself">super.h</warning>;
      h = (<warning descr="Variable 'h' is assigned to itself">h</warning>) = 1;
      arr[arr.length - 1] = <warning descr="Array element is assigned to itself">arr[arr.length - 1]</warning>;
      (arr[arr.length - 1]) = ((<warning descr="Array element is assigned to itself">arr[arr.length - 1]</warning>)) = 42;
      arr[arr.length - 1] = arr[arr.length - 2];
      arr[arr.length - 1] = arr[arr.length - 2] = 42;
      int i = 0;
      arr[i++] = arr[i++];
      arr[i += 1] = arr[i += 1];
      new int[]{1, 2, 3}[0] = new int[]{1, 2, 3}[0];
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