interface StaticBase<T> {
   static <T> void foo(T t) {}
}

class Foo implements StaticBase<Number> {
  static <T> void foo(T t){}
}

