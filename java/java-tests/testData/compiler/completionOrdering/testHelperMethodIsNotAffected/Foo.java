class Foo {

  public static void someMethod1() {

  }

  public static void someMethod2(String string) {

  }

  void m() {
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");
    nonNull("");

    someMethod1();
    someMethod1();
    someMethod1();
    someMethod2("");
    someMethod2("");
    someMethod2("");
    someMethod2("");
    someMethod2("");

    <caret>

  }

  public static <T> T nonNull(T t) {
    assert t != null;
    return t;
  }

}