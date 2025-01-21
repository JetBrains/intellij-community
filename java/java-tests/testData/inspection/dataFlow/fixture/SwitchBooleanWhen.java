import org.jetbrains.annotations.Nullable;

// IDEA-363270
public class SwitchBooleanWhen {
  private String testBooleanValue(Object value) {
    return switch (value) {
      case Boolean booleanValue when booleanValue -> "true";
      case Boolean booleanValue when <warning descr="Condition '!booleanValue' is always 'true'">!booleanValue</warning> -> "false";
      case null -> "null";
      default -> "unknown";
    };
  }
}