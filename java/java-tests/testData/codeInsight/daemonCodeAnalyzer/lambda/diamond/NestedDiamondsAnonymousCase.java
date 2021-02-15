import java.util.*;

class MyTest {
  private static  <K, V> HashMap<K, V> createMultiMap(boolean identityKeys) {
    return new HashMap<>(identityKeys ? new HashMap<>() : Collections.emptyMap()) {};
  }
}
