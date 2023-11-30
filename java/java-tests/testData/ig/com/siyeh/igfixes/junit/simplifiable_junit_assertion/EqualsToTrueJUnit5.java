class MyTest {
  {
    org.junit.jupiter.api.Assertions.<warning descr="'assertEquals()' can be simplified to 'assertTrue()'">assert<caret>Equals</warning>(true, foo(), () -> "message");
  }

  boolean foo() {return false;}
}