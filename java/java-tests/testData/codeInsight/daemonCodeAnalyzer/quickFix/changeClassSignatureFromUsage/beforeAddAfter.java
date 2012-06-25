// "Add type parameter to 'Foo'" "true"

class Foo<T extends Integer> {

  void method() {
    Foo<Integer, St<caret>ring> foo = new Foo();
  }
}