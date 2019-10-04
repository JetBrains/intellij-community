class C {
  void test(int n) {
    String s = switch (n) {
      case 1:
        yield "a";
      case 2:
        yield "b";
      case 3:
        <weak_warning descr="Duplicate branch in 'switch'">yield "a";</weak_warning>
      default:
        yield "";
    };
  }
}