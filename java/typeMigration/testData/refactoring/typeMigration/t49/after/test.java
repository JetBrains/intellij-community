import java.util.*;
class Test {
  Set<B> f;
  void foo(AbstractSet<E> s) {
    f = s;
  }

  class A {}
  class B extends A{}
}