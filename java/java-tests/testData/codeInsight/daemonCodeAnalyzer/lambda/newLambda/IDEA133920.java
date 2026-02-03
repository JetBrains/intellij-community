
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonMap;

class Main {

  public static void main(String[] args) {
    final Builder<Object, Object> builder = new Builder<>();

    String appName = "asdf";
    Map query = builder
      .put("size", 0)
      .put("query", singletonMap("bool",
                                 singletonMap("must",
                                              of(singletonMap("term", singletonMap("type.raw", appName)),
                                                 singletonMap("range", singletonMap("@timestamp", of("gt", "2014-12-01")))))))
      .build();

    System.out.println(query);
  }


  public static <K, V> Map<K, V> of(K k1, V v1) {
    return null;
  }


  static class Builder<K, V> {

    public Builder<K, V> put(K key, V value) {
      return this;
    }

    public HashMap<K, V> build() {
      return null;
    }
  }
}