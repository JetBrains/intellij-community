
import java.util.Map;
import static java.util.stream.Collectors.toMap;

class Test {
  Map<? extends Enum<?>, String> getValueByEnum() {
    return null;
  }

  Map<String, ? extends Enum<?>> getEnumByValue() {
    return getValueByEnum().entrySet().stream()
      .collect(toMap(Map.Entry::getValue, Map.Entry::getKey));
  }
}