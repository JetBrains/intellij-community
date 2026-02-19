// "Assert with JUnit 4 'Assert.assertNotNull(s)'" "true-preview"

import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public final class SomeJUnit4Test {
  @Nullable
  native String getNullableString();

  @Test
  public void test() {
    String s = getNullableString();
    //noinspection SimplifiableConditionalExpression
    assertTrue(s.isEm<caret>pty() ? true : false);
  }
}
