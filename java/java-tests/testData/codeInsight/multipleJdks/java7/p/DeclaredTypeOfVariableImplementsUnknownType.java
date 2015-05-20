package p;
abstract class B {
  void f(A a) {
    <error descr="Cannot access java.util.stream.Stream">a</error>.foo();
  }

  void f(java.util.List<? extends A> a) {
    <error descr="Cannot access java.util.stream.Stream">a.get(0)</error>.foo();
  }
}