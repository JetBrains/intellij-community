import org.junit.jupiter.api.Assertions;

class MyTest {
  {
      Assertions.assert<caret>Equals("literal", foo(), () -> "message");
  }

  String foo() {return null;}
}