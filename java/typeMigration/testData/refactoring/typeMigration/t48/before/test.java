import java.util.*;
class Test {
  void foo(AbstractSet<A> s) {
    Set<A> set = s;
  }

  class A {}
  class B extends A{}
}