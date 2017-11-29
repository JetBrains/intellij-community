interface I {
  Object CONST = new Object();
}

class C {
  void foo(Object o) {
    if (o == I.CONST) return;
    unknownMethod();
    if (<warning descr="Condition 'o != I.CONST' is always 'true'">o != I.CONST</warning>) return;
  }

  native void unknownMethod();
}