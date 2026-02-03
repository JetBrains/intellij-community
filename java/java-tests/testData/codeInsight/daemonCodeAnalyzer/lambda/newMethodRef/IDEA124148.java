import java.util.*;


import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;

class A<T>
{
  public <S> S foldRight(Supplier<S> value, BiFunction<T, Supplier<S>, Supplier<S>> f)
  {
    return null;
  }

  public static <S> Optional<S> h(A<S> flow)
  {
    return flow.foldRight(() -> Optional.empty(), (element, lazyResult) -> () -> Optional.of(element));

  }

  public static <S> Optional<S> hR(A<S> flow)
  {
    return flow.foldRight(Optional::empty, (element, lazyResult) -> () -> Optional.of(element));
  }
}