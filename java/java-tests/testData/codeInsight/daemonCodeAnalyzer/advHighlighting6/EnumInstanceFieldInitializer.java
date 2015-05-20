enum MyEnumTest {
  FOO;
  MyEnumTest Foo = <error descr="It is illegal to access static member 'FOO' from enum constructor or instance initializer">FOO</error>;

  {
    MyEnumTest foo = <error descr="It is illegal to access static member 'FOO' from enum constructor or instance initializer">FOO</error>;
  }
  static MyEnumTest Bar = FOO;

  MyEnumTest() {
    System.out.println(<error descr="It is illegal to access static member 'Bar' from enum constructor or instance initializer">Bar</error>);
  }
}
