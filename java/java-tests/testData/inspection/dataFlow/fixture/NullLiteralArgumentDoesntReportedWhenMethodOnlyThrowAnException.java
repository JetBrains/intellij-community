class Test {

  void m() {
    throwAnException(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
  }

  static void throwAnException(String arg) {
    throw new RuntimeException();
  }
}
