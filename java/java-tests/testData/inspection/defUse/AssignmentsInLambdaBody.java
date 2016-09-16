class Test {
  Runnable r = () -> {
    int i = <warning descr="Variable 'i' initializer '0' is redundant">0</warning>;
    i = 1;
    System.out.println(i);
  };
}