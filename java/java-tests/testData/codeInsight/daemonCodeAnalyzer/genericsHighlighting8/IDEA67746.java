import java.util.List;

abstract class B {
  abstract <T> void foo(List<List<T>> x);

  void bar(List<List> x){
    foo<error descr="'foo(java.util.List<java.util.List<T>>)' in 'B' cannot be applied to '(java.util.List<java.util.List>)'">(x)</error>;
  }
}