// "Assert with JUnit 5 'Assertions.assertNotNull(getNullableString())'" "false"

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SomeJUnit5Test {
  // tracked by the dataflow analysis as a getter, but not pure, so asserting it in-place would call it twice
  @Nullable
  native String getNullableString();

  @Test
  public void test() {
    assertTrue(getNullableString().isEm<caret>pty());
  }
}
