import org.jetbrains.annotations.Nullable;

class Boxed {
  String method() {
    if (maybeReturnsBool() == Boolean.TRUE) {
      return "foo";
    }
    return "baz";
  }

  public @Nullable Boolean maybeReturnsBool() {
    return Math.random() > 0.5;
  }
}
