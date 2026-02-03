interface Foo1 {
    void foo();
}
class Bar implements Foo1 {
  @Override
  public void foo() {
    System.out.println("hello");
  }
}