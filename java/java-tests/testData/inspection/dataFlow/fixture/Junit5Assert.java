import org.junit.jupiter.api.*;
import org.jetbrains.annotations.Nullable;

class Contracts {

  void foo(@Nullable Object o) {
    Assertions.assertNotNull(o);
    String s = o.toString();
  }

}