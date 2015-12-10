class A {
  private interface AsyncFunction<I, O> {
    Promise<O> apply(I input);
  }

  private interface Function<I, O> {
    O apply(I input);
  }

  private interface Promise<V> {
    <T1> Promise<T1> then(Function<? super V, T1> function);
    <T2> Promise<T2> then(AsyncFunction<? super V, T2> function);
  }

  private static Promise<Integer> calculateLength(String word) {
    return null;
  }

  public static void main(Promise<String> helloWorld) {
    helloWorld.then(A::calculateLength);
  }
}

class AAmbiguous {
  private interface AsyncFunction<I, O> {
    O apply(I input);
  }

  private interface Function<I, O> {
    O apply(I input);
  }

  private interface Promise<V> {
    <T1> Promise<T1> then(Function<? super V, T1> function);
    <T2> Promise<T2> then(AsyncFunction<? super V, T2> function);
  }

  private static Promise<Integer> calculateLength(String word) {
    return null;
  }

  public static void main(Promise<String> helloWorld) {
    helloWorld.then(AAmbiguous::<error descr="Cannot resolve method 'calculateLength'">calculateLength</error>);
  }
}
