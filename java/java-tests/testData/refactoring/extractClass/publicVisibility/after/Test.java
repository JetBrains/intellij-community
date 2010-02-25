class Test<T> {
    final Extracted<T> extracted = new Extracted<T>();

    void bar(){
        extracted.foos();
  }
  void foos(){
      extracted.foos();
  }
}