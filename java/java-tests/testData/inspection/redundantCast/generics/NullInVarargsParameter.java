class Test {
  void f(Class... classes) {
  }

  void g() {
    f(((Class[])null));
    f(((Class)null));
    f(((<warning descr="Casting 'null' to 'Class' is redundant">Class</warning>)null),
      ((<warning descr="Casting 'null' to 'Class' is redundant">Class</warning>)null));
  }
}