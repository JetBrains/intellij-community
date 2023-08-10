import org.junit.jupiter.api.Assertions;

class MyTest {
  {
      Assertions.assertTrue(foo(), () -> "message");
  }

  boolean foo() {return false;}
}