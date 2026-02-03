class Test<T> {
    public final Extracted<T> extracted = new Extracted<T>();

    void foo(T t){}
  void bar(){
    foo(extracted.myT);
  }
}