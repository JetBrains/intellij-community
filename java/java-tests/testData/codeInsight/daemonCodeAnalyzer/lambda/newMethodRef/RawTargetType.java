import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

class Test {
  interface I<K> {}
  
  {
    I i = foo(TreeMap::new);
  }

  <M extends Map<Integer, Integer>> I<M> foo(Supplier<M> mapFactory) {
    return null;
  }
}
