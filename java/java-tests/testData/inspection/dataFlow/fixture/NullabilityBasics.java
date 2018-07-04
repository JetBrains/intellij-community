import org.jetbrains.annotations.*;

class NullabilityBasics {

  void test2() {
    String x = getNullable();
    if (x == null) {
      System.out.println("x is null!");
    }
    if (isEmpty(x) && Math.random() > 0.5) {
      return;
    }
    System.out.println(x.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>());
  }

  @Nullable String getNullable() {
    return Math.random() > 0.5 ? null : "";
  }

  @Contract(value = "null -> true",pure = true)
  static boolean isEmpty(@Nullable String s) {
    return s == null || s.isEmpty();
  }

  void test(String x) {
    if (x == null) {
      System.out.println("x is null!");
    }
    System.out.println(x.<warning descr="Method invocation 'trim' may produce 'java.lang.NullPointerException'">trim</warning>());
  }
}