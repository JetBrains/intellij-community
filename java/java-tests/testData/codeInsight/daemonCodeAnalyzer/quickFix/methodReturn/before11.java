// "Make 'call()' return 'Callable<java.lang.Integer>' or ancestor" "true"
public class a extends CallableEx<String> {
  public Callable<String> call() {
    return new Callable<Inte<caret>ger>();
  }
}

class Callable<T> {
  Callable<T> call(){return null;}
}

class CallableEx<TE> extends Callable<TE>{}