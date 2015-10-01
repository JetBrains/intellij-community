class Test {
  interface One<T> {
    <S extends T> S save(S entity);
    <S extends T> Iterable<S> save(Iterable<S> entities);
  }
  static One<String> foo;
  public static void main(String[] args) throws Exception {
      foo.save <error descr="Ambiguous method call: both 'One.save(String)' and 'One.save(Iterable<String>)' match">(null)</error>;
  }
}