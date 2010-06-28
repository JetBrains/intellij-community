// "Make 'call' return 'int'" "true"
public class a implements Callable<String> {
  public String call() {
    return 4<caret>2;
  }
}

interface Callable<T> {
  T call();
}