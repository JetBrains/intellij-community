interface Function<S, R> {
  R fun(S s);
}

class Bar extends Function<String, ?>{
    public Object fun(String s) {
        <selection>return null;</selection>
    }
}