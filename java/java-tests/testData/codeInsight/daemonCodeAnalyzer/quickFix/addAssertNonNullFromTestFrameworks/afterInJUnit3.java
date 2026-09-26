// "Assert with JUnit 3 'assertNotNull(s)'" "true-preview"

import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

public class SomeJUnit3Test extends TestCase {
  @Nullable
  String getNullableString() {
    double random = Math.random();
    if (random > 0.75) return null;
    if (random > 0.50) return "";
    else return "bruh";
  }

  public void test() {
    String s = getNullableString();
      assertNotNull(s);
      assertTrue(s.isEmpty());
  }
}
