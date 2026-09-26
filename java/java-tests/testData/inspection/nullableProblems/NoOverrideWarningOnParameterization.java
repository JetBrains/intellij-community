import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NotNull;

@NullMarked
<error descr="Class 'MyMap' must either be declared abstract or implement abstract method 'size()' in 'Map'">public class MyMap<K, V> implements Map<K, V></error> {

  @Override
  public @Nullable V put(final K key, final V value) {
    return null;
  }
}