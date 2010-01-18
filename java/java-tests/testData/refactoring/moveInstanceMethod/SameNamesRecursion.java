public class A {
    void f<caret>oo(B b) {
      b.foo("");
      if (false) {
        foo(b);
      }
  }
}

class B {
  void foo(String s) {
    System.out.println(s);
  }
}


