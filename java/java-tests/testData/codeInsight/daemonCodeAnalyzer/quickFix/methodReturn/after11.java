// "Make 'call' return 'Callable<java.lang.Integer>'" "true"
public class a extends CallableEx<Integer> {
  public Callable<Integer> call() {
    return new Callable<Integer>();
  }
}

class Callable<T> {
  Callable<T> call(){return null;}
}

class CallableEx<TE> extends Callable<TE>{}