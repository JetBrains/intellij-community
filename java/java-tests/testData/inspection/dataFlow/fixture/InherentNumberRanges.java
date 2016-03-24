class Foo {

  private void foo(byte myByte) {
    if (<warning descr="Condition 'myByte == 0xFF' is always 'false'">myByte == 0xFF</warning>) {
      System.out.println("wrong");
    }
    if (<warning descr="Condition 'myByte != 0xFFF' is always 'true'">myByte != 0xFFF</warning>) {
      System.out.println("wrong");
    }
    if (myByte == (byte) 0xFF) {
      System.out.println("right");
    }
  }

  void foo2(int i) {
    boolean a = <warning descr="Condition 'i < 0x80000000L' is always 'true'">i < 0x80000000L</warning>;
    boolean f = <warning descr="Condition 'i > 0x7fffffff' is always 'false'">i > 0x7fffffff</warning>;
  }

  void f(int k) {
    if (<warning descr="Condition 'k <= Integer.MAX_VALUE' is always 'true'">k <= Integer.MAX_VALUE</warning>);
    if (<warning descr="Condition 'k > Integer.MAX_VALUE' is always 'false'">k > Integer.MAX_VALUE</warning>);
    if (<warning descr="Condition 'k >= Integer.MIN_VALUE' is always 'true'">k >= Integer.MIN_VALUE</warning>);
  }

  void doo() {
    for (long l = Long.MIN_VALUE; l < Long.MIN_VALUE + 10; l++) {
      System.out.println(l);
    }
  }
}