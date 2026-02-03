enum IDEA56239 {
  A, B() {
    {
      System.out.println(<error descr="Accessing enum constant from enum instance initializer is not allowed">A</error>);
      System.out.println(<error descr="Accessing static field from enum instance initializer is not allowed">FOO</error>);
      System.out.println(FOO1);
      System.out.println(<error descr="Accessing enum constant from enum instance initializer is not allowed">C</error>);
    }
  }, C(<error descr="Cannot refer to enum constant 'D' before its definition">D</error>), D;

  public static String FOO = "";
  public static final String FOO1 = "";

  IDEA56239() {
  }

  IDEA56239(IDEA56239 t) {
    System.out.println(<error descr="Accessing enum constant from enum constructor is not allowed">A</error>);
    System.out.println(<error descr="Accessing static field from enum constructor is not allowed">FOO</error>);
    System.out.println(FOO1);
  }

  {
    System.out.println(<error descr="Accessing enum constant from enum instance initializer is not allowed">A</error>);
    System.out.println(<error descr="Accessing static field from enum instance initializer is not allowed">FOO</error>);
    System.out.println(FOO1);
  }

  void foo() {
    System.out.println(A);
    System.out.println(FOO);
    System.out.println(FOO1);
  }
}