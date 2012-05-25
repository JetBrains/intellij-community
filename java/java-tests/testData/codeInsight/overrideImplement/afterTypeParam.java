interface Function<S> {
  void fun(Function<S> function);
}

class Bar extends Function<String>{
    public void fun(Function<String> function) {
        <selection>//To change body of implemented methods use File | Settings | File Templates.</selection>
    }
}