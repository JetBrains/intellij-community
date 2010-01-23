public class A {
  void foo() {
    bar();
  }

  void b<caret>ar() {}
}

class B extends A {
  void bar(){
    super.bar();
  }
}