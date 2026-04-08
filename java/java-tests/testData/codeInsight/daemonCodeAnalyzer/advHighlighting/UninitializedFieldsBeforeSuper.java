class Test {
  private final String foo;
  private final String bar = foo;

  public Test(String foo) {
    this.foo = foo;
    super();
  }

  static void main() {
    System.out.println(new Test("test").bar);
  }
}
class Test2 {
  private final String foo;
  private final String bar = <error descr="Variable 'foo' might not have been initialized">foo</error>;

  public Test2(String foo) {
    super();
    this.foo = foo;
  }

  static void main() {
    System.out.println(new Test2("test").bar);
  }
}