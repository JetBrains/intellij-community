import java.util.function.IntFunction;

class Outer<K> {
  public Outer() { }

  {
    //wrong number of parameters as well as a wrong expected type
    final IntFunction<Outer[]> aNew = Outer::<error descr="Cannot resolve constructor 'Outer'">new</error>;
  }
}