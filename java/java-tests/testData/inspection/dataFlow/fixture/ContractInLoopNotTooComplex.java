import org.jetbrains.annotations.Contract;

class Foo {

  public void main(String s) {
    for (int i = 0; i < 10; i++) {
      assertTrue("str", s != null);
      s.hashCode();
    }
  }

  @Contract("_, false->fail")
  void assertTrue(String msg, boolean value) {
    if (!value) throw new RuntimeException();
  }

}