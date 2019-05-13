// "Replace with findFirst()" "true"

import java.util.List;
import java.util.Optional;

public class Main {
  public Optional<String> trim(String s) {
    return s.isEmpty() ? Optional.empty() : Optional.of(s.trim());
  }

  public Optional<String> test(List<Object> objects) {
    Optional<String> result = Optional.empty();
    for(Object obj : obj<caret>ects) {
      if(obj instanceof String) {
        result = trim((String)obj);
        break;
      }
    }
    return result;
  }
}