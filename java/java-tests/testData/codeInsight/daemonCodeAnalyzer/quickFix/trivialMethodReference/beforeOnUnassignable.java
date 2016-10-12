// "Replace with qualifier" "false"
class Test {
  interface A {
    void m();
  }

  interface B extends A {}

  void foo(B b) {}

  void bar(A a){
    foo(a::<caret>m);
  }
}