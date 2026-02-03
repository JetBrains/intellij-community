
import java.util.function.BiConsumer;
import java.util.function.Predicate;

class MyTest {
  {
    BiConsumer<Predicate<? extends Runnable>, Predicate<? extends Runnable>> or = MyTest::<error descr="Incompatible equality constraint: capture of ? extends Runnable and capture of ? extends Runnable">or</error>;
  }

  private static <E extends Runnable> void or(Predicate<E> left, Predicate<E> right) {}
}
