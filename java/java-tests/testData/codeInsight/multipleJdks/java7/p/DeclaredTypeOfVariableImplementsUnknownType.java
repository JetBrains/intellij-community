package p;
abstract class B {
  void f(A a) {
    <error descr="Cannot access java.util.stream.Stream">a</error>.foo();
  }
}