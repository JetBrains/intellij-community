
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

abstract class NestedLambda {
  abstract <T, V> V[] map2Array(Collection<? extends T> collection, Class<V> aClass, Function<? super T, ? extends V> mapper);

  void m(Collection<String> methods) {
    Object[] r = map2Array(methods, Consumer.class, method -> (Consumer<Integer>) resolveResult -> {
      int i = resolveResult.intValue();
    });
    Object[] r1 = map2Array(methods, Consumer.class, method -> (<warning descr="Casting 'resolveResult -> {...}' to 'Consumer' is redundant">Consumer</warning>) resolveResult -> { });
  }
}
