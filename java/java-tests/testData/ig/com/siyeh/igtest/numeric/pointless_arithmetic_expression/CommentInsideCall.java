class Foo2 {
  void t2(String s) {
    boolean v = <warning descr="'s.length//simple end comment () * 1' can be replaced with 's.length//simple end comment
      ()'">s.length//simple <caret>end comment
      () * 1</warning> + 2 > 4 || s.isEmpty();
  }
}
