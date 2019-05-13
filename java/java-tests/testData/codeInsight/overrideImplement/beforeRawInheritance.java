class Obj<T> {}
abstract class A1<X>{
    abstract void foo(Obj<String> x);
}

class B1 extends A1{
  <caret>
}
