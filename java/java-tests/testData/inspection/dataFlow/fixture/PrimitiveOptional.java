import java.util.*;

class Test {
  void test() {
    int x = OptionalInt.of(123).orElse(-1);
    System.out.println(<warning descr="Condition 'x == 123' is always 'true'">x == 123</warning>);
    System.out.println(<warning descr="Condition 'x == -1' is always 'false'">x == -1</warning>);
    int x1 = OptionalInt.of(123).orElseGet(() -> -1);
    System.out.println(<warning descr="Condition 'x1 == 123' is always 'true'">x1 == 123</warning>);
    System.out.println(<warning descr="Condition 'x1 == -1' is always 'false'">x1 == -1</warning>);
    OptionalLong.of(1000).ifPresent(val -> {
      if(<warning descr="Condition 'val > 0' is always 'true'">val > 0</warning>) {
        System.out.println(val);
      }
    });
    long y = OptionalLong.empty().orElse(10);
    System.out.println(<warning descr="Condition 'y == 12' is always 'false'">y == 12</warning>);
    System.out.println(<warning descr="Condition 'y == 10' is always 'true'">y == 10</warning>);
    double z = OptionalDouble.empty().<warning descr="The call to 'getAsDouble' always fails, according to its method contracts">getAsDouble</warning>();

  }
}

