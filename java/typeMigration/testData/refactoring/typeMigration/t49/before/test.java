import java.util.*;
class Test {
  Set<A> f;
  void foo(AbstractSet<A> s) {
    f = s;
  }

  class A {}
  class B extends A{}
}