import static java.util.Collections.EMPTY_LIST;

public class AnonymousClassJUnit4 {

  @org.junit.Test
  public void testNotNull() {
    EMPTY_LIST != null<caret>
  }

  private void assertNotNull() {}
}