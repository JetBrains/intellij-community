interface Function<S> {
  void fun(Function<S> function);
}

class Bar extends Function<String>{
  <caret>
}