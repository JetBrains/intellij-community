class C {
  void test() {
    double x = <weak_warning descr="Multiple occurrences of 'Math.sin(4)'">Math.sin(4)</weak_warning>;
    double y = <weak_warning descr="Multiple occurrences of 'Math.sin(4)'">Math.sin(4)</weak_warning>;
  }
}