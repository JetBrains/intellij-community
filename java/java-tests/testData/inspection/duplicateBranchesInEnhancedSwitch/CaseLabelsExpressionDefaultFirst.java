class C {
  void test(int n) {
    String s = switch (n) {
      default:
      case 1:
        yield "a";
      case 2:
        yield "b";
      case 3:
        <weak_warning descr="Branch in 'switch' is a duplicate of the default branch">yield "a";</weak_warning>
    };
  }
}