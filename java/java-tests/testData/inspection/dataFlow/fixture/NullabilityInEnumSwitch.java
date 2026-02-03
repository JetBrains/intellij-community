import org.jetbrains.annotations.Nullable;

// IDEA-357456
public class NullabilityInEnumSwitch {

  enum Option {
    A(false),
    B(true),
    C(true);

    private final boolean needsValue;

    Option(boolean needsValue) {
      this.needsValue = needsValue;
    }
  }

  static boolean test(@Nullable String value, Option option) {
    if (option.needsValue && value == null) throw new IllegalArgumentException();

    return switch (option) {
      case A -> true;
      // Too hard to analyze: we should go back into needsValue initializer
      case B -> value.<warning descr="Method invocation 'isEmpty' may produce 'NullPointerException'">isEmpty</warning>();
      case C -> value.<warning descr="Method invocation 'startsWith' may produce 'NullPointerException'">startsWith</warning>("a");
    };
  }

}