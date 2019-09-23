import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;
class AsyncContext {

  public static <T> T computeOnEdt(Supplier<T> supplier) {
    return null;
  }

  public static <K, V> ConcurrentMap<K, V> create(Function<? super K, ? extends V> computeValue,
                                                  Supplier<? extends ConcurrentMap<K, V>> mapCreator) {
    return null;
  }

  private static final Provider provider = null;

  private final Map<Component, Provider> myProviders = create(key->
                                                                computeOnEdt(() -> {
                                                                  return dataKey -> {
                                                                    return computeOnEdt(() -> {
                                                                      return provider.get<caret>Data(dataKey);
                                                                    });
                                                                  };
                                                                }),

                                                              AsyncContext::createConcurrentWeakKeySoftValueMap
  );

  public static <K,V> ConcurrentMap<K,V> createConcurrentWeakKeySoftValueMap() {
    return null;
  }
}

abstract class Component {}

interface Provider {
  Object getData(String key);
}
