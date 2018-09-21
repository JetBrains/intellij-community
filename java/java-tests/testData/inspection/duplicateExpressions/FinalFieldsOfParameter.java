class C {
  void foo(M m) {
    if ((<weak_warning descr="Multiple occurrences of 'm.a != null && (m.b.length() > 0 || m.c.length() > 0)'">m.a != null && (m.b.length() > 0 || m.c.length() > 0)</weak_warning>)
        || (m.d != null && m.d.length() > 0)) {
      bar(m, 1);
      if (<weak_warning descr="Multiple occurrences of 'm.a != null && (m.b.length() > 0 || m.c.length() > 0)'">m.a != null && (m.b.length() > 0 || m.c.length() > 0)</weak_warning>) {
        bar(m, 2);
      }
      if (m.d != null && m.d.length() > 0) {
        bar(m, 3);
      }
    }
  }

  void bar(M m, int i) {}

  static class M {
    final String a;
    final String b;
    final String c;
    final String d;

    M(String a, String b, String c, String d) { this.a = a;this.b = b;this.c = c;this.d = d;}
  }
}