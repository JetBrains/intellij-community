class Ref {
  int i;
  Object obj;
}

class Modifier {
  void foo(Ref r) { r.i = 239; r.obj = <warning descr="Assigning 'null' value to non-annotated field">null</warning>; }
}

class Test {
  void foo(Ref r, Modifier m) {
    if (r.i == 0) {
      m.foo(r);
      if (r.i == 0) {
        return;
      }
    }
    if (r.obj == null) {
      m.foo(r);
      if (r.obj == null) {
        return;
      }
    }
  }

}