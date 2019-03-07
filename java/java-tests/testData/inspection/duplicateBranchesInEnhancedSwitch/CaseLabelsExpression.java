class C {
  void test(int n) {
    String s = switch (n) {
      case 1:
        break "a";
      case 2:
        break "b";
      case 3:
        <weak_warning descr="Duplicate branch in 'switch'">break "a";</weak_warning>
      default:
        break "";
    };
  }
}