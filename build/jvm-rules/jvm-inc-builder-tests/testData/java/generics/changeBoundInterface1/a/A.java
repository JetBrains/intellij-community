public class A<T extends Value & I> {
  void foo(T t) {t.foo();}
}