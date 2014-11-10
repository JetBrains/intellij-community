interface Foo<T> {
  void run(T t, int myInt);
}

public class A implements Foo<String> {
  @Overr<caret>
}
