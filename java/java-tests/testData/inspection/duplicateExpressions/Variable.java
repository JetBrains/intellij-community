class C {
  final int field;

  C(int i) {
    field = i;
  }

  void test(int param) {
    int a = field;
    int b = field;

    int c = param;
    int d = param;

    int e = <weak_warning descr="Multiple occurrences of 'field + param'">field + param</weak_warning>;
    int f = <weak_warning descr="Multiple occurrences of 'field + param'">field + param</weak_warning>;
  }
}