class MyTest {
  {
    org.junit.jupiter.api.Assertions.assert<caret>Equals(true, foo(), () -> "message");
  }

  boolean foo() {return false;}
}