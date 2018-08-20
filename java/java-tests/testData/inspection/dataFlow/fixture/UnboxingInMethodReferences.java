class X {
  interface I {
    int get();
  }
  
  interface J {
    Integer get();
  }
  
  I is = <warning descr="Use of 'X::m' would need unboxing which may produce 'NullPointerException'">X::m</warning>;
  J j = X::m;

  @org.jetbrains.annotations.Nullable
  static Integer m() {
    return null;
  }
}