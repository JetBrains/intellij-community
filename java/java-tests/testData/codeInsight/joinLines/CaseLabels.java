class A {
  void test(int x) {
    switch (x) {
      <selection>case 0:
      case 1:
      case 2:</selection>
      case 3:
        System.out.println("hello");
      default:
    }
  }
}
