class Main {
  void foo() throws ReflectiveOperationException {
    Class.forName("java.lang.StringBuffer<caret>");
  }
}