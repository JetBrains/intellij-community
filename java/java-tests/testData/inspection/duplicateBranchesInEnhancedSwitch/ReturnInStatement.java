class C {
  String foo(int n) {
    return switch (n) {
      case 1 -> "A";
      case 2 -> "B";
      case 3 -> <weak_warning descr="Duplicate branch in 'switch'">"A";</weak_warning>
      default -> "";
    };
  }
}