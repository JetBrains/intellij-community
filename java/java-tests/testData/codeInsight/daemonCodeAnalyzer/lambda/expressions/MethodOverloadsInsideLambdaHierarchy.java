import java.util.function.*;
class A {
  <T extends String> void foo(Function<A, T> f){}
  
  static <O> Object bar(Object o) {
    return null;
  }
  
  static <K> A bar(A a) {
    return null;
  }
  
  static <L> L boo(Supplier<L> s) {
    return null;
  }

  <P> P baz() {
    return null;
  }

  {
    foo(a -> bar(a).boo(() -> baz()));
  }
}