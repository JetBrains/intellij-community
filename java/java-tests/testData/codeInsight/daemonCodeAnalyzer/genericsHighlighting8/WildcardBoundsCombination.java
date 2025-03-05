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
    c2.forEach(<error descr="Incompatible types. Found: 'java.util.function.Consumer<java.util.Map.Entry<K,V>>', required: 'java.util.function.Consumer<? super capture<? super java.util.Map.Entry<K,V>>>'">consumer</error>(a));
  }
}

