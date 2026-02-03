// "Add constructor parameter" "true"
import org.jetbrains.annotations.*;
class A {
  @Nullable private final Object <caret>field;

  A(String... strs) {
  }

}