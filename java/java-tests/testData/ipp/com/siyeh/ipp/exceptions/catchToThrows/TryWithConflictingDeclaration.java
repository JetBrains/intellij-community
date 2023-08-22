class TryWithConflictingDeclaration {

  abstract void f(String s) throws Exception;

  void m() {
    try {
            /* important comment */
      String s = "hello";
      f();
      // another comment
    } catch (Exception <caret>ignore) { }
    String s = "bye";
    System.out.println(s);
  }
}