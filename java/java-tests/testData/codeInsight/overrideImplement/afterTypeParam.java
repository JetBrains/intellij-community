interface Function<S> {
  void fun(Function<S> function);
}

class Bar extends Function<String>{
    public void fun(Function<String> function) {
        <caret>
    }
}