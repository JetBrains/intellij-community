
class Foo {

  public static final Bar[] bars= new Bar[] {Bar.callMe("a", 0, "A", "B", "C"), Bar.callMe("b", 1, "A", "B")};
}


class Bar {

  public Bar(String a, int nr, String... args) {
  }

  public static Bar call<caret>Me(String a, int nr, String ... args) {
    return new Bar(a, nr, args);
  }
}