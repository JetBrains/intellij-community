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
    c2.forEach(consumer<error descr="'consumer(java.util.function.Consumer<java.util.Map.Entry<K1,V1>>)' in 'NachCollections' cannot be applied to '(java.util.function.Consumer<java.util.Map.Entry<K,V>>)'">(a)</error>);
  }
}

