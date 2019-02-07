class C {
  void foo(int n) {
    switch (n) {
      case 1 -> throw new IllegalArgumentException();
      case 2 -> throw new IllegalStateException();
      case 3 -> <weak_warning descr="Duplicate branch in 'switch' statement">throw new IllegalArgumentException();</weak_warning>
    }
  }
}