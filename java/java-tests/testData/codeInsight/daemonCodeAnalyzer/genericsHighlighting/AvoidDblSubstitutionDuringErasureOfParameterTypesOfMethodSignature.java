class Foo<T> {
  void run(T param) {}

  void anonymousClass() {
    new Foo<T[]>() {
      @Override
      void run(T[] param) {}
    };
  }
}