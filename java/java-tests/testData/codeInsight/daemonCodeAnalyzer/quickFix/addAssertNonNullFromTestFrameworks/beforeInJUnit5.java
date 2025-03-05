// "Assert with JUnit 5 'Assertions.assertNotNull(s)'" "true-preview"

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SomeJUnit5Test {
  @Nullable
  String getNullableString() {
    double random = Math.random();
    if (random > 0.75) return null;
    if (random > 0.50) return "";
    else return "bruh";
  }

  @Test
  public void test() {
    String s = getNullableString();
    assertTrue(s.isEm<caret>pty());
  }
}
