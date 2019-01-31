class C {
  String test(int i) {
    return switch (i) {
      case 0 -> null;
      case 1 -> <weak_warning descr="Duplicate result expression in 'switch' expression">(null);</weak_warning>
      default -> "";
    };
  }
}