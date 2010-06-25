// "Make 'call' return 'Callable<java.lang.Integer>'" "true"
public class a extends Callable<Integer> {
  public Callable<Integer> call() {
    return new Callable<Integer>();
  }
}

class Callable<T> {
  Callable<T> call(){return null;}
}