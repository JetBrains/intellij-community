interface Foo1 {
  void foo();
}
class Bar implements Foo1 {
  public void <caret>foo() {
    System.out.println("hello");
  }
}