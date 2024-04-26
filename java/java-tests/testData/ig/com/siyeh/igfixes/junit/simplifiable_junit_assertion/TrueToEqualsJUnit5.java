import org.junit.jupiter.api.Assertions;

class MyTest {
  {
      Assertions.<warning descr="'assertTrue()' can be simplified to 'assertEquals()'"><caret>assertTrue</warning>(foo().equals(-1), () -> "message");
  }

  Object foo() {return null;}
}