class C {
  int i;

    private void foo() {
        foo(i);
    }

    private static void foo(int i) {
    if (true) {
      foo(i);
    }
    System.out.println(i);
  }
  
  {
    foo();
  }
}