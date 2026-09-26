// "Remove guard expression" "true-preview"
import org.jetbrains.annotations.*;

import java.math.*;

public class Foo {

  record Price(BigDecimal value) {
  }

  @Nullable
  public Price price;

  public String test() {
    return switch (price) {
      case Price p -> switch (p.value().multiply(BigDecimal.valueOf(100)).intValue()) {
        case int i when i < 0 -> throw new IllegalStateException("Price is negative: " + p);
        case 0 -> "free";
        case 1, 2, 3, 4, 5, 6, 7, 8, 9 -> "cheap";
        case int i when i < 100_00 -> "regular";
        case int i when i < 1000_00 -> "expensive";
        case int i when i >= <caret>1000_00 -> "pricey";
        case int i when i == 1000_01 -> "pricey";
        default -> throw new IllegalStateException("Price is out of range: " + p);
      };
      case null -> "priceless";
    };
  }
}