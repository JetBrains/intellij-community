// "Add type parameter to 'Foo'" "true"

class Foo<caret><T extends Integer, S, K extends Integer> {

  void method() {
    Foo<Integer, String, Integer> foo = new Foo();
  }
}