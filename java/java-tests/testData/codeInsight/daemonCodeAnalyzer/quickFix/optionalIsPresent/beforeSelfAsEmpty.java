// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.Optional;

public class Main {
  public Optional<String> get() {
    Optional<String> port = Optional.ofNullable(System.getProperty("abc");
    Optional<String> result;
    if(po<caret>rt.isPresent()) {
      result = Optional.of(port.get().trim());
    } else {
      result = port;
    }
    return result;
  }
}