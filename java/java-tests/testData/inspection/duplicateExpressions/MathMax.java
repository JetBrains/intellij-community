class C {
  void test(int i, int j) {
    int a = <weak_warning descr="Multiple occurrences of 'Math.max(i, j)'">Math.max(i, j)</weak_warning>;
    int b = <weak_warning descr="Multiple occurrences of 'Math.max(i, j)'">Math.max(i, j)</weak_warning>;
  }
}