class Test {
  public static void test(String[] args) {
    for (String <warning descr="The value of foreach iteration parameter 'arg' is never used">arg</warning> : args) {
      arg = "test";
      System.out.println(arg);
    }
    for (String ignored : args) {
    }

    for (String arg : args) {
      if (args.length == 1) {
        System.out.println(arg);
      }
      arg = "test";
      System.out.println(arg);
    }

  }
}