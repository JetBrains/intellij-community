// "Add type parameter to 'Foo'" "true"

class Foo<T extends Integer> {

  void method() {
    Foo<St<caret>ring, Integer> foo = new Foo();
  }
}