class Main {
  public static <T> void make(final Consumer<? super T> consumer) {
    Sink<T> accept = (Sink<T>) consumer::accept;
    Consumer<T> accept1 = (Consumer<T>)consumer::accept;
  }
}

interface Sink<T> extends Consumer<T> {

}

interface Consumer<T> {
  public void accept(T t);
}
