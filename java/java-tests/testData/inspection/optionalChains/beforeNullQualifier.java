// "Replace null check with orElse("")" "false"
import java.util.Optional;

class Test {
  public static final class Example {
    private final Optional<String> optional;

    public Example(Optional<String> optional) { this.optional = optional; }

      public static Example get() {
        return null;
      }

    public Optional<String> optional() { return optional; }
  }

  public static String test() {
    Example example = Example.get();
    String value = example == null ? null : example.optional().<caret>orElse(null);
    return value == null ? "" : value;
  }
}