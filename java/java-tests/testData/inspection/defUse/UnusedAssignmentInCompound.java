class Foo {
  void test() {
    int i = 5;
    i += (<warning descr="The value '10' assigned to 'i' is never used">i</warning> = 10);
    System.out.println(i);
  }

  void test2() {
    int i = 5;
    i += (<warning descr="The value '10' assigned to 'i' is never used">i</warning> = 10) + 1;
    System.out.println(i);
  }
}