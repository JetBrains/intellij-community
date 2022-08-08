// "Replace Optional presence condition with functional style expression" "true-preview"

import java.util.Optional;

public class Test {
  private Power power;

  interface Power {
    static Optional<Power> parseValue(String value) {
      return Optional.empty();
    }
  }

  void testOpt(String powerValue) {
    Optional<Power> optPower = Power.parseValue(powerValue);
    if (optPower<caret>.isPresent()) {
      power = optPower.get();
    }
  }
}