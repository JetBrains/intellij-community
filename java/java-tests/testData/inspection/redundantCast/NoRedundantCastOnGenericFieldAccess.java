import java.util.*;

class ConfigurationObject<T> {
  Map<String, T> values = new HashMap<>();

  static void doPut(String key, String value, ConfigurationObject<?> section) {
    ((ConfigurationObject<String>)section).values.put(key, value);
  }
}
