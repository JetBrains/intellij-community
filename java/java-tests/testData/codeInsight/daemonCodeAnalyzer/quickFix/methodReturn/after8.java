// "Make 'call' return 'int'" "true"
public class a implements Callable<Integer> {
  public Integer call() {
    return 42;
  }
}

interface Callable<T> {
  T call();
}