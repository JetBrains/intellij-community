// "Add 'B' as 2nd parameter to method 'foo'" "true"
public class S {

  void foo(A a, B b) {
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
  