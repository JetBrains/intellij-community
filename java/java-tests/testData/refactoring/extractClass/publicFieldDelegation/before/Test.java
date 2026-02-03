class Test<T> {
  public T myT;
  void foo(T t){}
  void bar(){
    foo(myT);
  }
}