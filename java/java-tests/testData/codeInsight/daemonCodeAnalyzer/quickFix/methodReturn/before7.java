// "Make 'call()' return 'java.lang.Integer' or ancestor" "true"
public class a implements Callable<String> {
  public String call() {
    return new Int<caret>eger(0);
  }
}

interface Callable<T> {
  T call();
}