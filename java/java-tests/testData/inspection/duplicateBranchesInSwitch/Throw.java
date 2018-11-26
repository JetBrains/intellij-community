class C {
  String foo(int n) {
    switch (n) {
      case 1:
        <weak_warning descr="Duplicate branch in 'switch' statement">throw new IllegalArgumentException("A");</weak_warning>
      case 2:
        throw new IllegalStateException("A");
      case 3:
        <weak_warning descr="Duplicate branch in 'switch' statement">throw new IllegalArgumentException("A");</weak_warning>
    }
    return "";
  }
}