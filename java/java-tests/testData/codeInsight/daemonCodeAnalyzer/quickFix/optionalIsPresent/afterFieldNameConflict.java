// "Replace Optional.isPresent() condition with functional style expression" "true"

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
      optPower.ifPresent(value -> power = value);
  }
}