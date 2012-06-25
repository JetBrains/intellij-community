// "Add type parameter to 'Foo'" "true"

class Foo<T extends Integer, K extends Integer> {

  void method() {
    Foo<Integer, St<caret>ring, Integer> foo = new Foo();
  }
}