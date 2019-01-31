class C {
  String foo(int n) {
    switch (n) {
      case 1:
        return "A";
      case 2:
        return "B";
      case 3:
        <weak_warning descr="Duplicate branch in 'switch' statement">return "A";</weak_warning>
    }
    return "";
  }
}