class VarArgs {
  private static void foo(String value, int...<flown11>ints) {
      System.out.println(value + " " + java.util.Arrays.asList(ints));
      int <caret>anInt = <flown1>ints[1];
  }

  private static void bar(String value, int...<flown1111>ints) {
      foo(value, <flown111>ints);
  }

  private static void baz(String value) {
      bar<flown11111>("d", <flown111111>2, <flown111112>3, <flown111113>4);
  }
}
