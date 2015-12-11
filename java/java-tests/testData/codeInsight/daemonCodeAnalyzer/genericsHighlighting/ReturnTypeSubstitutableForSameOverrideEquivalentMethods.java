interface I<T, S extends Throwable> {
  int foo(T x);
  void foo(S x);
}

class A implements I<Throwable, Throwable>{
  public <error descr="'foo(Throwable)' in 'A' clashes with 'foo(T)' in 'I'; attempting to use incompatible return type">void</error> foo(Throwable x) { }
}
