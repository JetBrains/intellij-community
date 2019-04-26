class C {
  void test() {
    accept(n -> n * n);
    accept(n -> n * n);

    accept(n -> {
      int a = <weak_warning descr="Multiple occurrences of 'n * n'">n * n</weak_warning>;
      int b = <weak_warning descr="Multiple occurrences of 'n * n'">n * n</weak_warning>;
      return a + b;
    });
  }

  void accept(I i) {
    i.f(0);
  }

  interface I {
    int f(int n);
  }
}