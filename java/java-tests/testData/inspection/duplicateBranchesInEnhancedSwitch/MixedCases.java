class C {
  enum T {A, B,}

  void foo(Object o) {
    switch (o) {
      case Integer _:
      case T.A:
        System.out.println("1");
        break;
      case String _:
        <weak_warning descr="Duplicate branch in 'switch'">System.out.println("1");</weak_warning>
        break;
      default:
        System.out.println("3");
    }
  }
}