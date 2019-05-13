package p;

class Foo {
  void bar() {}
  void bar(int i){
    bar();
  }

  {
    bar();
    bar(1);
  }
}
