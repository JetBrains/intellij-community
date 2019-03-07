class C {
  void test(int n) {
    String s;
    switch (n) {
      case 0: s = a(); break;
      case 1: <weak_warning descr="Duplicate branch in 'switch'">s = a();</weak_warning> break;
      case 2: <weak_warning descr="Branch in 'switch' is a duplicate of the default branch">s = b();</weak_warning> break;
      default: s = b();
    }
  }
  String a() {return "a";}
  String b() {return "b";}
}