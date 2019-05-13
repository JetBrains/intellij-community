// "Make 'get' return 'Callable<java.lang.Integer>'" "true"
interface Gettable<T> {
  Callable<Integer> get();
}

public class Issue<T> implements Gettable<T> {
  public Callable<Integer> get() {

    return new Callable<Integer>() {
      public Integer call() {
        return 0;
      }
    };

  }
}

class Callable<T> {}