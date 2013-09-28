enum IDEA56239 {
  A, B() {
    {
      System.out.println(<error descr="It is illegal to access static member 'A' from enum constructor or instance initializer">A</error>);
      System.out.println(<error descr="It is illegal to access static member 'FOO' from enum constructor or instance initializer">FOO</error>);
      System.out.println(FOO1);
      System.out.println(<error descr="It is illegal to access static member 'C' from enum constructor or instance initializer">C</error>);
    }
  }, C(<error descr="Illegal forward reference">D</error>), D;

  public static String FOO = "";
  public static final String FOO1 = "";

  IDEA56239() {
  }

  IDEA56239(IDEA56239 t) {
    System.out.println(<error descr="It is illegal to access static member 'A' from enum constructor or instance initializer">A</error>);
    System.out.println(<error descr="It is illegal to access static member 'FOO' from enum constructor or instance initializer">FOO</error>);
    System.out.println(FOO1);
  }

  {
    System.out.println(<error descr="It is illegal to access static member 'A' from enum constructor or instance initializer">A</error>);
    System.out.println(<error descr="It is illegal to access static member 'FOO' from enum constructor or instance initializer">FOO</error>);
    System.out.println(FOO1);
  }

  void foo() {
    System.out.println(A);
    System.out.println(FOO);
    System.out.println(FOO1);
  }
}
