class C {
  void test(int n) {
    String s = switch (n) {
      case 1:
        yield "a"; // one comment
      case 2:
        yield "b";
      case 3:
        yield "a"; // another comment
      default:
        yield "";
    };
  }
}