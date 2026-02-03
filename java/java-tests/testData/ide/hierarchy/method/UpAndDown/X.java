class X<C> {
  void foo(C c) {}
}
class Y<D extends Number> extends X<D> {
  void foo(D d) {}
}
class Z extends Y<Integer> {
  void foo(Integer i) {}
}
class T extends Y<Integer> {
  void foo(Long l) {} // doesn't override
}