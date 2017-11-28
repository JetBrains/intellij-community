import java.lang.annotation.*;

@interface A { }

@Target(ElementType.TYPE_USE)
@interface TA { }

class C {
  @Override
  public String toString(@TA C this) { return ""; }

  @Override
  public boolean equals(@TA C this, @TA Object other) { return false; }

  @interface Anno { String f(Anno this); }

  void m0() {
    Runnable r = (C <error descr="Receivers are not allowed outside of method parameter list">C.this</error>) -> { };
  }

  void m1a(<error descr="Modifier 'final' not allowed here">final</error> C this) { }
  void m1b(<error descr="'@A' not applicable to type use">@A</error> C this) { }

  void m2(@TA Object other, @TA C <error descr="The receiver should be the first parameter">this</error>) { }

  void m3a(@TA <error descr="The receiver type does not match the enclosing class type">Object</error> this) { }
  void m3b(@TA <error descr="The receiver type does not match the enclosing class type">int</error> this) { }

  void m4a(C C.this) { }
  void m4b(C <error descr="The receiver name does not match the enclosing class type">C.X.this</error>) { }

  void m5() {
    class L {
      L(C C.this) { }
    }
  }

  static void sm1(@TA Object <error descr="The receiver cannot be used in a static context">this</error>) { }

  C(C <error descr="The receiver cannot be used in a static context">this</error>) { }

  static class X {
    X(X <error descr="The receiver cannot be used in a static context">this</error>) { }
  }

  class B {
    B(C C.this) { }
    B(<error descr="The receiver type does not match the enclosing class type">B</error> C.this, int p) { }
    B(C <error descr="The receiver name does not match the enclosing class type">B.this</error>, long p) { }
    B(C <error descr="The receiver name does not match the enclosing class type">this</error>, float p) { }
  }

  static class CI<T> {
    void m1(CI<T> this) { }
    <T> void m2(<error descr="The receiver type does not match the enclosing class type">CI<T></error> this) { }
    void m3(<error descr="The receiver type does not match the enclosing class type">CI<String></error> this) { }
  }
}