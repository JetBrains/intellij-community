// "Make 'call()' return 'java.lang.Integer' or ancestor" "true"
public class a implements Callable<Integer> {
  public Integer call() {
    return new Integer(0);
  }
}

interface Callable<T> {
  T call();
}