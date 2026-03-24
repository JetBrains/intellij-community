import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@NullMarked
class Main {
  public static void main(String[] args) {
    Attributable attributable = new Attributable(Map.of(
      "name", "Alice",
      "role", "admin"
    ));

    Function<Attributable, Set<String>> attributeCollector = value -> Registry.ATTRIBUTE.stream()
      .map(value::getAttribute)
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    Set<String> attributes = getter(attributeCollector).apply(attributable);

    System.out.println(attributes);
  }

  static final class Registry {
    static final Set<String> ATTRIBUTE = Set.of("name", "role", "missing");
  }

  static final class Attributable {
    private final Map<String, String> attributes;

    Attributable(Map<String, String> attributes) {
      this.attributes = attributes;
    }

    @Nullable
    String getAttribute(String key) {
      return attributes.get(key);
    }
  }

  private static <T, R> Function<T, R> getter(Function<T, R> getter) {
    return getter;
  }
}