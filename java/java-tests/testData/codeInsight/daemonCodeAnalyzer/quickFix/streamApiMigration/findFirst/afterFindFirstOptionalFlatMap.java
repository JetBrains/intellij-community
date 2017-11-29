// "Replace with findFirst()" "true"

import java.util.List;
import java.util.Optional;

public class Main {
  public Optional<String> trim(String s) {
    return s.isEmpty() ? Optional.empty() : Optional.of(s.trim());
  }

  public Optional<String> test(List<Object> objects) {
      Optional<String> result = objects.stream().filter(obj -> obj instanceof String).findFirst().flatMap(obj -> trim((String) obj));
      return result;
  }
}