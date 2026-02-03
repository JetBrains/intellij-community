import java.util.OptionalInt;

public class Test {
  public static void main(String[] args) {
    test(OptionalInt.of(124));
  }

  static void test(OptionalInt x) {
    x = OptionalInt.of(10);
    x = OptionalInt.of(x.getAsInt() + 1);
    x = OptionalInt.of(x.getAsInt() + 2);
    System.out.println(x.getAsInt());
    System.out.println(x.getAsInt() + 1);
  }
}