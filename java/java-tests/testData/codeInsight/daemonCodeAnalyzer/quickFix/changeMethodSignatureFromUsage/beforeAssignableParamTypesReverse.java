// "Add 'A' as 1st parameter to method 'foo'" "true-preview"
public class S {

  void foo(A a) {
  }

  void bar(B b) {
    A a = getA();
    foo(a, <caret>b);
  }

  A getA() {
    return new A();
  }
}

class A {
}

class B extends A {
}
  