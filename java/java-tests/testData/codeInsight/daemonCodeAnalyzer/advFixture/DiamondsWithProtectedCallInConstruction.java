package c;
import a.*;

class Derived extends Base {
  {
    Foo<String> b = new Foo<>(super.createString()) {};
  }
  
  class Foo<T> {
    Foo(T t) {
    }
  }
}