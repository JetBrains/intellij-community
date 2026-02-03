class C {
  int foo(int x) {
     return <selection>x+1</selection>;
  }
}

class D extends C {
  int foo(int x) {
    return super.foo(x);
  }
}