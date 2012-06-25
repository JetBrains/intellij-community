// "<html>Change signature of 'Foo' to 'Foo&lt;<b>S</b>, T extends Integer, <b>S1</b>&gt;'</html>" "true"

class Foo<caret><S, T extends Integer, S1> {

  void method() {
    Foo<String, Integer, String> foo = new Foo();
  }
}