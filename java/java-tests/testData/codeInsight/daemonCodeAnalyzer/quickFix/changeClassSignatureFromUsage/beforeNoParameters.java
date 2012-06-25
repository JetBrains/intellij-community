// "Add type parameter to 'Foo'" "true"

class Foo {

  void method() {
    Foo<St<caret>ring> foo = new Foo();
  }
}