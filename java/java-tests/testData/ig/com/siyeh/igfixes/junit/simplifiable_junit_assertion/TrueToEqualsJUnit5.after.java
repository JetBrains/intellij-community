import org.junit.jupiter.api.Assertions;

class MyTest {
  {
      Assertions.assertEquals(-1, foo(), () -> "message");
  }

  Object foo() {return null;}
}