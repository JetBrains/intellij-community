class C {
  void test(int n) {
    String s = switch (n) {
      default:
      case 1:
        break "a";
      case 2:
        break "b";
      case 3:
        <weak_warning descr="Branch in 'switch' is a duplicate of the default branch">break "a";</weak_warning>
    };
  }
}