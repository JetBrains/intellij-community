import org.junit.jupiter.api.Assertions;

class MyTest {
  {
      Assertions.<warning descr="'assertTrue()' can be simplified to 'assertEquals()'">assert<caret>True</warning>("literal".equals(foo()), () -> "message");
  }

  String foo() {return null;}
}