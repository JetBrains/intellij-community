// "Make 'call' return 'java.lang.Integer'" "true"
public class a implements Callable<Integer> {
  public Integer call() {
    return new Integer(0);
  }
}

interface Callable<T> {
  T call();
}