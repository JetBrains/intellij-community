// "Add 'A' as 2nd parameter to method 'foo'" "true"
public class S {

  void foo(A a, A a1) {
  }

  void bar(B b) {
    A a = getA();
    foo(b, a);
  }

  A getA() {
    return new A();
  }
}

class A {
}

class B extends A {
}
  