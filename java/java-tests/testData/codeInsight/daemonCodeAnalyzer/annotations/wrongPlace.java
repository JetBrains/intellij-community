@interface Anno { }

abstract class C {
  abstract void f();

  void m() {
    <error descr="Annotations are not allowed here">@Anno</error> f();
  }
}