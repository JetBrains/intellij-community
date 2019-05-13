package p;
abstract class B {
  void f(A a) {
    <error descr="Cannot access java.util.stream.Stream">a.foo</error>();

    Object o = a;

    a.bar();
    
    <error descr="Cannot access java.util.stream.Stream">a.myField</error>;

    a.a();
  }

  void f(java.util.List<? extends A> a) {
    <error descr="Cannot access java.util.stream.Stream">a.get(0).foo</error>();
  }
}