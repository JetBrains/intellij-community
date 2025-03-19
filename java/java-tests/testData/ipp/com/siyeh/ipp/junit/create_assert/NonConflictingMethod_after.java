import static java.util.Collections.EMPTY_LIST;
import static org.junit.Assert.assertNotNull;

public class NonConflictingMethod extends Super {

  @org.junit.Test
  public void testNotNull() {
      assertNotNull(EMPTY_LIST);
  }
}
class Super {
  private static void assertNotNull() {}
}