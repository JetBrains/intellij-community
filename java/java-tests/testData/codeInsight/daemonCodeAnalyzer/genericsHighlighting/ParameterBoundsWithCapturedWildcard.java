class Test<T > {
  public static interface EventListener<V extends String> {}
  public void addListener (EventListener<<error descr="Type parameter '? extends T' is not within its bound; should extend 'java.lang.String'">? extends T</error>> listener) {}
  public void addListener1(EventListener<? super T> listener) {}
}
