// "Make 'call' return 'Callable<java.lang.Integer>'" "true"
public class a extends Callable<String> {
  public Callable<String> call() {
    return new Callable<Inte<caret>ger>();
  }
}

class Callable<T> {
  Callable<T> call(){return null;}
}