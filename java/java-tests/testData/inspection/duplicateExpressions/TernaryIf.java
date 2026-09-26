class C {
  private int foo(String a, String b, boolean f, int n, int m, T t) {
    if (a == null && (n == t.a() || <weak_warning descr="Multiple occurrences of 'n + (f ? m : 0)'">n + (f ? m : 0)</weak_warning> == t.b())) {
      return 0;
    }
    if (a == null) {
      return bar(<weak_warning descr="Multiple occurrences of 'n + (f ? m : 0)'">n + (f ? m : 0)</weak_warning>, b);
    }
    else {
      return a.length();
    }
  }

  private int bar(int i, String s) {return 0;}

  static  class T {
    int a() {return 0;}
    int b() {return 0;}
  }
}