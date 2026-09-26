package test.something;

public class SwitchRecordPrimitiveJava25 {
  static void main(String[] args) {
    Integer a = 1;
    switch (a) {
      case <error descr="Primitive types in patterns, instanceof and switch are not supported at language level '25'">int _</error> -> System.out.println("1");
      default -> throw new IllegalStateException("Unexpected value: " + a);
    }
  }
}
