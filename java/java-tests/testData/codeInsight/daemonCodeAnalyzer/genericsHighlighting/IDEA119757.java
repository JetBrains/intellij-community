import java.util.List;

class Foo<T extends V,V> {
  Foo(List<? extends T> l) {
  }
}
class Bar {
  void foo(Foo<String,String> foo) {}

  void bar(List<String> l) {
    foo<error descr="'foo(Foo<java.lang.String,java.lang.String>)' in 'Bar' cannot be applied to '(Foo<java.lang.String,java.lang.Object>)'">(new Foo<>(l))</error>;
    foo<error descr="'foo(Foo<java.lang.String,java.lang.String>)' in 'Bar' cannot be applied to '(Foo<java.lang.String,java.lang.Object>)'">(f(l))</error>;
  }

  <T1 extends V1, V1> Foo<T1, V1> f(List<? extends T1> l) {return null;}
}
