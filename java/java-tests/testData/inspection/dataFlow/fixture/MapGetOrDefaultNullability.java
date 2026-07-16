import java.util.Map;

import org.jetbrains.annotations.Nullable;

class MapGetOrDefaultNullability {
  static void nullableDefaultValue() {
    var map = Map.of("1", "2");
    if (map.getOrDefault("2", null) == null) {
      System.out.println("missing");
    }
  }

  static void notNullDefaultValue() {
    var map = Map.of("1", "2");
    if (<warning descr="Condition 'map.getOrDefault(\"2\", \"fallback\") == null' is always 'false'">map.getOrDefault("2", "fallback") == null</warning>) {
      System.out.println("impossible");
    }
  }

  static void unknownValueNullability(Map<String, String> map) {
    if (map.getOrDefault("2", "fallback") == null) {
      System.out.println("possibly null");
    }
  }

  static void nullableValue(Map<String, @Nullable String> map) {
    if (map.getOrDefault("2", "fallback") == null) {
      System.out.println("mapped to null");
    }
  }
}
