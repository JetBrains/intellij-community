class Test {
  //fix false positive warning
  static void nullable(String <warning descr="Method will throw an exception when parameter is null">s</warning>) {
    switch (s) {
      case "xyz" -> System.out.println("xyz");
      case null, default -> System.out.println("else");
    }
  }

  static void notNullable(String s) {
    switch (s) {
      case "xyz" -> System.out.println("xyz");
      case default -> System.out.println("else");
    }
  }

  public static void main(String[] args) {
    nullable(<warning descr="Passing 'null' argument to non-annotated parameter">null</warning>);
    notNullable(<warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>);
  }
}