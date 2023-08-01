import org.junit.jupiter.api.Assertions;

class MyTest {
  {
      Assertions.assert<caret>True(foo().equals(-1), () -> "message");
  }

  Object foo() {return null;}
}