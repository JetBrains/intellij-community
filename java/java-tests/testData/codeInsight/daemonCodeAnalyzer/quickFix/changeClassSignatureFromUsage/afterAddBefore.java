// "Add type parameter to 'Foo'" "true"

class Foo<caret><S, T extends Integer> {

  void method() {
    Foo<String, Integer> foo = new Foo();
  }
}