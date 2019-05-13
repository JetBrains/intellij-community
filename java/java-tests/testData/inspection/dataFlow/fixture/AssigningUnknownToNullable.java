import org.jetbrains.annotations.Nullable;

class BrokenAlignment {

  String unknown(String s) { return s; }

  public void example(@Nullable String value) {
    value = unknown(<warning descr="Argument 'value' might be null but passed to non-annotated parameter">value</warning>);
    if (value.contains("%")) {
    }
  }
}