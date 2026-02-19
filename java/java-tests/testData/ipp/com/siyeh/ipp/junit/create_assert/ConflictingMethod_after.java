import org.junit.Assert;

import static java.util.Collections.EMPTY_LIST;

public class AnonymousClassJUnit4 {

  @org.junit.Test
  public void testNotNull() {
      Assert.assertNotNull(EMPTY_LIST);
  }

  private void assertNotNull() {}
}