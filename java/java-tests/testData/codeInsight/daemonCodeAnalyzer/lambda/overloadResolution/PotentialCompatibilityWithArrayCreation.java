interface A {
  void enableInspections(B... providers);
  void enableInspections(Runnable r, String... inspections);
}

interface B {
  Class[] get();
}

class C {
  void foo(A a) {
    a.enableInspections(() -> new Class[]{});
  }
}