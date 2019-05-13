
import java.util.function.Supplier;

class Test<T> {
  {
    Supplier<T> aNew = T::<error descr="Cannot resolve constructor 'T'">new</error>;
  }
}
