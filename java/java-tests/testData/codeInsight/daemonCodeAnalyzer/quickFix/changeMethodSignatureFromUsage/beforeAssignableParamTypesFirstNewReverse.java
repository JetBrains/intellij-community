// "Add 'A' as 2nd parameter to method 'foo'" "true-preview"
public class S {

  void foo(A a) {
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
  