class Test<R> {
  void foo(Param<R> param) {
    if (param.r() == null) {
    }
  }

  void bar(R r){
    foo(new Param<>(r));
  }
}