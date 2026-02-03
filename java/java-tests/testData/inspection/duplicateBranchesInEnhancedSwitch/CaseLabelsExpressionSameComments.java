class C {
  void test(int n) {
    String s = switch (n) {
      case 1:
        yield "a"; // same comment
      case 2:
        yield "b";
      case 3:
        <weak_warning descr="Duplicate branch in 'switch'">yield "a"; // same comment</weak_warning>
      default:
        yield "";
    };
  }
}