import java.util.function.IntFunction;

class Outer<K> {
  public Outer() { }

  {
    //wrong number of parameters as well as a wrong expected type
    final IntFunction<Outer[]> aNew = <error descr="Bad return type in method reference: cannot convert Outer to Outer[]">Outer::new</error>;
  }
}