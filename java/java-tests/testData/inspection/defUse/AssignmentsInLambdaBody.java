class Test {
  Runnable r = () -> {
    int i = <warning descr="Variable 'i' initializer '0' is redundant">0</warning>;
    i = 1;
    System.out.println(i);
  };
  java.util.function.IntSupplier is = () -> switch (1) {
    default -> {
      String s;
      System.out.println(<warning descr="The value '\"hi\"' assigned to 's' is never used">s</warning> = "hi");
      yield 1;
    }
  };
}