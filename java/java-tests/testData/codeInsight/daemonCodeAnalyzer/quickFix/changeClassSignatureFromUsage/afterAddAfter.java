// "Add type parameter to 'Foo'" "true"

class Foo<caret><T extends Integer, S> {

  void method() {
    Foo<Integer, String> foo = new Foo();
  }
}