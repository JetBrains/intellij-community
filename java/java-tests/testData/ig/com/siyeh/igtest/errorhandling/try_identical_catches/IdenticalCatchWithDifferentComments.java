class C {
  void foo() {
    try {
      bar();
    } catch (Ex1 e) {
      // unique comment
    } <info descr="'catch' branch identical to 'Ex1' branch">catch (Ex2 <caret>e)</info> {
      /* another comment */
    }
  }

  void bar() throws Ex1, Ex2 {}

  static class Ex1 extends Exception {}
  static class Ex2 extends Exception {}
}