
import java.util.function.BiConsumer;
import java.util.function.Predicate;

class MyTest {
  {
    BiConsumer<Predicate<? extends Runnable>, Predicate<? extends Runnable>> or = MyTest::<error descr="Invalid method reference: Predicate<capture of ? extends Runnable> cannot be converted to Predicate<E>">or</error>;
  }

  private static <E extends Runnable> void or(Predicate<E> left, Predicate<E> right) {}
}
