// "Add 'B' as 1st parameter to method 'foo'" "true-preview"
public class S {

  void foo(B b, A a) {
  }

  void bar(B b) {
    A a = getA();
    foo(<caret>b, a);
  }

  A getA() {
    return new A();
  }
}

class A {
}

class B extends A {
}
  