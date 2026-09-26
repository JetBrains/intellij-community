// "Introduce variable and assert with JUnit 5 'Assertions.assertNotNull(nullableString)'" "true-preview"

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SomeJUnit5Test {
  // tracked by the dataflow analysis as a getter, but not pure, so it must not be called twice
  @Nullable
  native String getNullableString();

  @Test
  public void test() {
    assertTrue(getNullableString().isEm<caret>pty());
  }
}
