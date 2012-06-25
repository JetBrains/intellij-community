// "Add type parameter to 'Foo'" "true"

class Foo<caret><S> {

  void method() {
    Foo<String> foo = new Foo();
  }
}