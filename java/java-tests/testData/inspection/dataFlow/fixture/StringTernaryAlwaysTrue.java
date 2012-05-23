class Foo {
  public void foo() {
    final boolean flag = true;

    bar(<warning descr="Condition 'flag' is always 'true'">flag</warning> ? "a" : "b",
      <warning descr="Condition 'flag' is always 'true'">flag</warning> ? new String[]{"aa"} : new String[]{"bb"}
    );
  }

  void bar(String b, String[] a) {
  }
}