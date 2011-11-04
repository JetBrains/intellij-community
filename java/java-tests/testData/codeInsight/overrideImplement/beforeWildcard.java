interface Function<S, R> {
  R fun(S s);
}

class Bar extends Function<String, ?>{
  <caret>
}