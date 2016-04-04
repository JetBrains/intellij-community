class A<T> {
  public void foo(T t){}
}

class B<K> extends A<K> {
  public void foo(String s) {}
}
class <error descr="Methods foo(String) from B and foo(T) from A are inherited with the same signature">C</error> extends B<String> {}

class D<T> {
  public void foo(T t){}
  public void foo(String s) {}
}

class <error descr="Methods foo(T) from D and foo(String) from D are inherited with the same signature">E</error> extends D<String> {}
class F extends D<String> {
  public void foo(String s) {}
}