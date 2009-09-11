class A {}

class List<T> extends A {
  T t;
}


class Test {
  void foo (){
    List y = null;
    A a = y;

    y.t = "";
  }
}
