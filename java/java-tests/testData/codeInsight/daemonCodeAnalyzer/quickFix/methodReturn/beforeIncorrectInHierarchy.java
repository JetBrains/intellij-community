// "Make 'get' return 'Callable<java.lang.Integer>'" "true"
interface Gettable<T> {
  Callable<Integer> get();
}

public class Issue<T> implements Gettable<T> {
  public void get() {

    return new Call<caret>able<Integer>() {
      public Integer call() {
        return 0;
      }
    };

  }
}

class Callable<T> {}