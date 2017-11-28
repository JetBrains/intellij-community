import java.util.Optional;
import java.util.function.Function;

abstract class View {
  {
    foo(View::returnType).orElse(void.class);
  }

  abstract <H> Optional<H> foo(Function<View, H> f);

  public Class<?> returnType() {
    return null;
  }
}

