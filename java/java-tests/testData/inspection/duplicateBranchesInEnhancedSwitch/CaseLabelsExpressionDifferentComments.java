class C {
  void test(int n) {
    String s = switch (n) {
      case 1:
        break "a"; // one comment
      case 2:
        break "b";
      case 3:
        break "a"; // another comment
      default:
        break "";
    };
  }
}