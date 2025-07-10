// "Fix all 'Nullability and data flow problems' problems in file" "true"
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
class Main {
  String test() {
    return n<caret>ull;
  }

  @NotNull String test2() {
    return null;
  }
}
