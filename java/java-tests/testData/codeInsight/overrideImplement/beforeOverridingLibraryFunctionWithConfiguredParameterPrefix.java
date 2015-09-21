interface IntFunction<T> {
  int apply(T t);
}

class A implements IntFunction<String> {
  <caret>
}