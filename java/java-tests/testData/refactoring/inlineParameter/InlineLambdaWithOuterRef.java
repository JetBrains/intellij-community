interface A {
  void foo(int i);
}
class B {
  B(A <caret>a) {
    System.out.println(a);
  }
}
class C {
  {
    int k = 42;
    B b = new B(i1 -> i1 + k);
  }
}