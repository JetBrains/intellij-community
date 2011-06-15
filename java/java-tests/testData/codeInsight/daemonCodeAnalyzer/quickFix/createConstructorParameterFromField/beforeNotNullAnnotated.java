// "Add constructor parameter" "true"
import org.jetbrains.annotations.*;
class A {
  @NotNull private final Object <caret>field;

  A(String... strs) {
  }

}