import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

// IDEA-356144
@NullMarked
public class JSpecifyUpperBound {
  private <R extends @Nullable Object> R openSupplier(Supplier<? extends R> func) {
    return func.get();
  }
}

@NullMarked
class BoundTest<T extends Object, T2, T3 extends @Nullable Object> {
  T retT(@Nullable T x) { return <warning descr="Expression 'x' might evaluate to null but is returned by the method declared as @NullMarked">x</warning>; }

  T2 retT2(@Nullable T2 x) { return <warning descr="Expression 'x' might evaluate to null but is returned by the method declared as @NullMarked">x</warning>; }

  T3 retT3(@Nullable T3 x) { return x; }
}
