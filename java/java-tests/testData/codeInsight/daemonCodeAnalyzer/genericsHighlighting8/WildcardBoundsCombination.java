import java.util.*;
import java.util.function.Consumer;

class NachCollections<K,V> {
  <K1, V1> Consumer<Map.Entry<K1, V1>> consumer(Consumer<Map.Entry<K1, V1>> c) {
    return null;
  }

  public void forEach(Collection<? extends Map.Entry<K,V>> c1,
                      Collection<? super Map.Entry<K,V>> c2,
                      Consumer<Map.Entry<K, V>> a) {
    c1.forEach(consumer(a));
    c2.forEach(<error descr="Incompatible types. Required Consumer<? super capture of ? super Entry<K, V>> but 'consumer' was inferred to Consumer<Entry<K1, V1>>:
no instance(s) of type variable(s) K1, V1 exist so that capture of ? super Entry<K, V> conforms to Entry<K1, V1>">consumer(a)</error>);
  }
}

