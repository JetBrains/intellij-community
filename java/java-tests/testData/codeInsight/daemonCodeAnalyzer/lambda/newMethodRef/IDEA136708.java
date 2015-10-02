
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

class Test {
  {
    Map<?, ?> map = new HashMap<>();
    map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    map.entrySet().stream().collect(Collectors.toMap((entry) -> entry.getKey(), Map.Entry::getValue));
    map.entrySet().stream().collect(Collectors.toMap((entry) -> entry.getKey(), (entry) -> entry.getValue()));
    map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue()));
  }
}
