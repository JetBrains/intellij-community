import org.junit.jupiter.api.Assertions;

public class JUnit5Test {

  @org.junit.jupiter.api.Test
  public void xxx() {
      Assertions.assertEquals(2, 1 + 1);
  }
}