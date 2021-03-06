interface I<T> {
  void m(T t);
}

class MyTest {
  {
    I<String> i = new I<>() {
      public void m(String s) {}
    };

    <caret>i.m("");
  }
}