package foo.bar.baz;

enum E1 {
  A
}

enum E2 {
  B
}

class C {
  void test(E1 e1, E2 e2) {
    if (e1 == foo.bar.baz.E1.A) {
      System.out.println("here");
    }
    if (e2 == foo.bar.baz.E2.B) {
      System.out.println("there");
    }
  }
}