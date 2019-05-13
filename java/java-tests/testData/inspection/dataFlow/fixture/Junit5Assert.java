import org.junit.jupiter.api.*;
import org.jetbrains.annotations.Nullable;

class Contracts {

  void foo(@Nullable Object o) {
    Assertions.assertNotNull(o);
    String s = o.toString();
  }

  void foo2(@Nullable Object o) {
    Assertions.assertNotNull(o, "message");
    String s = o.toString();
  }

  void foo3(java.util.function.BooleanSupplier o) {
    Assertions.assertTrue(o, "message");
  }

  void foo3(boolean b) {
    Assertions.assertTrue(b, "message");
    if (<warning descr="Condition '!b' is always 'false'">!<weak_warning descr="Value 'b' is always 'true'">b</weak_warning></warning>) {
      System.out.println("how?");
    }
  }

}