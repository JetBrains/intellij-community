import static java.util.Collections.EMPTY_LIST;

public class NonConflictingMethod extends Super {

  @org.junit.Test
  public void testNotNull() {
    EMPTY_LIST != null<caret>
  }
}
class Super {
  private static void assertNotNull() {}
}