// "Replace with lambda" "true"
import java.util.function.*;
class Test {

  public static Promise<Integer> some() {
    return PromiseUtils.compose(
      new Promise<String>(),
      v -> new Promise<Long>(),
            result -> 0);
  }
}

class Promise<T> {}

class PromiseUtils {
  public static <A, B, C> Promise<C> compose(Promise<A> aPromise, Function<A, Promise<B>> abTransform, Function<B, C> resultTransform) {
    return null;
  }
}