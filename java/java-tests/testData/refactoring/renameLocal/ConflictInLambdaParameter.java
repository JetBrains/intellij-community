class ConfictInLambdaParameter {
  public void consume(Consumer<Object> c) {
  }

  public void bug() {
    consume(o -> {       // line 1
      consume(o1<caret> -> {}); // line 2
    });
  }
}