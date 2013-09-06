import org.jetbrains.annotations.Contract;

public class Foo {

  public void main(String[] args) {
    for (int i = 0; i < 10; i++) {
      assertTrue("str", true);
    }
  }

  @Contract("_, false->fail")
  void assertTrue(String msg, boolean value) {

  }

}