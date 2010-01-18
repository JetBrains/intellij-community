public class A {
    void f<caret>oo(B b) {
      b.foo("");
    }
}

class B {
  void foo(String s) {
    System.out.println(s);
  }
}


