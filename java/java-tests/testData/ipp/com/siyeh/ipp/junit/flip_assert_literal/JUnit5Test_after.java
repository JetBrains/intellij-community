import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JUnit5Test {

  @org.junit.jupiter.api.Test
  public void xxx() {
      assertFalse(1 + 1 != 2);
  }
}