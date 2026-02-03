class A {
  void f<caret>oo(int i) {}
}

class B extends A {
  void foo(int i) {
    System.out.println(i);
  }
}