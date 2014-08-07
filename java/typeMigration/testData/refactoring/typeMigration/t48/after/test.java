import java.util.*;
class Test {
  void foo(AbstractSet<B> s) {
    Set<B> set = s;
  }

  class A {}
  class B extends A{}
}