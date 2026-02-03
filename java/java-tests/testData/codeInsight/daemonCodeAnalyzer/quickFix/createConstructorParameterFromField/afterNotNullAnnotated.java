// "Add constructor parameter" "true"
import org.jetbrains.annotations.*;
class A {
  @NotNull private final Object field;

  A(@NotNull Object field, String... strs) {
      this.field = field;<caret>
  }

}