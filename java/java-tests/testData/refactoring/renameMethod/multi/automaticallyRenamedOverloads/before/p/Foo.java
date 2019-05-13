package p;

class Foo {
  void foo() {}
  void foo(int i){
    foo();
  }

  {
    foo();
    foo(1);
  }
}
