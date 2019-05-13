import java.util.function.IntSupplier;

public class LambdaInlining {
  void testLambdaInline() {
    int x = ((IntSupplier) (() -> {
      if (Math.random() > 0.5) {
        return 4;
      }
      return 5;
    })).getAsInt();
    if (<warning descr="Condition 'x == 6' is always 'false'">x == 6</warning>) {
      System.out.println("oops");
    }
  }

  void testLambdaTryCatch() {
    int x = ((IntSupplier)(() -> {
      try {
        return Math.random() > 0.5 ? 2 : 3;
      }
      catch (Exception ex) {

      }
      return 1;
    })).getAsInt();
    if(<warning descr="Condition 'x == 0' is always 'false'">x == 0</warning>) {
      System.out.println("oops");
    }
  }

  void testLambdaTryFinally() {
    int x = ((IntSupplier)() -> {
      try {
        return 10;
      } finally {
        return 20;
      }
    }).getAsInt();
    if(<warning descr="Condition 'x == 30' is always 'false'">x == 30</warning>) {
      System.out.println("oops");
    }
    if(<warning descr="Condition 'x < 30' is always 'true'">x < 30</warning>) {
      System.out.println("always");
    }
  }
}