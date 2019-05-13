public class Test {
  A f;
  int bar(){
    return f.foo();
  }
}
class A {
  int foo(){
    return 0;
  }
}

class B extends A{}