// "Add exception to method signature" "true-preview"
class Test {

  interface I<E extends Exception> {
    void call() throws E;
  }

  public static <E extends Exception> void method(I<E> i) throws E {
    i.call();
  }
}