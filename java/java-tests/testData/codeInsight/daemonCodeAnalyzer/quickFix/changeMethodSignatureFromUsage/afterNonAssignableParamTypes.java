// "Add 'String' as 2nd parameter to method 'foo'" "true-preview"
public class S {

  void foo(A a, String s) {
  }

  void bar(B b) {
    A a = getA();
    foo(a, <caret>"");
  }

  A getA() {
    return new A();
  }
}

class A {
}

class B extends A {
}
  