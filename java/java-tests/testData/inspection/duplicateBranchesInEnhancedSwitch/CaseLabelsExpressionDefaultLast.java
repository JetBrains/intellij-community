class C {
  void test(int n) {
    String s = switch (n) {
      case 1:
        <weak_warning descr="Branch in 'switch' is a duplicate of the default branch">break "a";</weak_warning>
      case 2:
        break "b";
      case 3:
      default:
        break "a";
    };
  }
}