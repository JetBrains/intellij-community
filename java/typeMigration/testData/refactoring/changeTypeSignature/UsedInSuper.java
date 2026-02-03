class A<T> {
  T foo(){return null;}
  T bar(){return foo();}
}

class B extends A<S<caret>tring> {
  String foo(){return super.bar();}
  String bar(){return super.foo();}
}