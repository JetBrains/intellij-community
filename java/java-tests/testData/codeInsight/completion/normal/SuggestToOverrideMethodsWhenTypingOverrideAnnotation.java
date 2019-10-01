interface Foo<T> {
  void run(T t, int myInt);
  void run2(T t, int myInt);
}

class A implements Foo<String> {
  @Overr<caret>
}
