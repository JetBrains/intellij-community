// "<html>Change signature of 'Foo' to 'Foo&lt;<b>S</b>, T extends Integer, <b>S1</b>&gt;'</html>" "true"

class Foo<T extends Integer> {

  void method() {
    Foo<String, Integer, St<caret>ring> foo = new Foo();
  }
}