// "Add 'A' as 1st parameter to method 'foo'" "true"
public class S {

  void foo(A a1, A a) {
  }

  void bar(B b) {
    A a = getA();
    foo(a, b);
  }

  A getA() {
    return new A();
  }
}

class A {
}

class B extends A {
}
  