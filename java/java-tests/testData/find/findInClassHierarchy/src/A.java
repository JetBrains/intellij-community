public class A {
  void foo(Object o) {
    if (o instanceof String){}
  }
}

class AImpl extends A {
  void foo(Object o) {
    if (o instanceof String){}
  }
}

class B {
  void bar(Object o) {
    if (o instanceof String){}
  }
}

