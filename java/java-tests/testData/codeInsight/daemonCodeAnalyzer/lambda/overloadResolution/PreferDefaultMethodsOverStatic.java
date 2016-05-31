class Test {
  interface I {
    default void f() {}
  }

  interface J {
    static void f() {}
  }

  static class IJ implements I, J {}
  static class JI implements J, I {}

  public static void main(String[] args) {
    new IJ(). f();
    new JI(). f();

    IJ.<error descr="Non-static method 'f()' cannot be referenced from a static context">f</error>();
    JI.<error descr="Non-static method 'f()' cannot be referenced from a static context">f</error>();

    J.f();
  }
}

class Test2 {
  interface I {
    static void f(String <warning descr="Parameter 's' is never used">s</warning>) {}
  }

  interface J<T> {
    default void f(T <warning descr="Parameter 't' is never used">t</warning>) {}

    //another pair
    default void j(T <warning descr="Parameter 't' is never used">t</warning>) {}
    static  void j(String <warning descr="Parameter 's' is never used">s</warning>) {};
  }

  static class IJ implements I, J<String> {}

  public static void main(IJ s, J<String> j) {
    s.f("");

    <error descr="Static method may be invoked on containing interface class only">j.j("");</error>
  }

}