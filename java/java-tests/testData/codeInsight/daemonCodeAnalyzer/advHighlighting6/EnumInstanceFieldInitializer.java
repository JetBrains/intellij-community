enum MyEnumTest {
  FOO;
  MyEnumTest Foo = <error descr="Accessing enum constant from enum instance field initializer is not allowed">FOO</error>;

  {
    MyEnumTest foo = <error descr="Accessing enum constant from enum instance initializer is not allowed">FOO</error>;
  }
  static MyEnumTest Bar = FOO;

  MyEnumTest() {
    System.out.println(<error descr="Accessing static field from enum constructor is not allowed">Bar</error>);
  }
}
