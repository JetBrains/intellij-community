import org.jetbrains.annotations.Nullable;

class Boxed {
  String method(boolean value) {
    if (Boolean.TRUE.equa<caret>ls(returnsBool(value))) {
      return "foo";
    }
    return "baz";
  }

  public @Nullable Boolean returnsBool(boolean value) {
    return Math.random() > 0.5 ? true : false;
  }
}
