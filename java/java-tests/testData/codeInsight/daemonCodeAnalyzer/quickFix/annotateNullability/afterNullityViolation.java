// "Fix all 'Nullability and data flow problems' problems in file" "true"
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
class Main {
  @Nullable String test() {
    return null;
  }

  @Nullable String test2() {
    return null;
  }
}
