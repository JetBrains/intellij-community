// "Add constructor parameter" "true"
import org.jetbrains.annotations.*;
class A {
  @Nullable private final Object field;

  A(@Nullable Object field, String... strs) {
      this.field = field;
  }

}