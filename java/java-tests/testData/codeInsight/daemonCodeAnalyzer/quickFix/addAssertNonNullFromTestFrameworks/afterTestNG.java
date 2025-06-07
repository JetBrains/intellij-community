// "Assert with TestNG 'Assert.assertNotNull(s)'" "true-preview"

import org.jetbrains.annotations.Nullable;
import org.testng.Assert;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

public class SomeTestNGTest {
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
      Assert.assertNotNull(s);
      assertTrue(s.isEmpty());
  }
}
