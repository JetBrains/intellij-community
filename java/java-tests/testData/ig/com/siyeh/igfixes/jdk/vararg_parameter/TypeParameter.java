import org.jetbrains.annotations.NotNull;

class FieldSet<T> {
  public FieldSet<T> set(<caret>T @NotNull ... fields) {
    return this;
  }

  static void main() {
    final FieldSet<String> set = new FieldSet<>();
    set.set(/* 1 = */ "one", /* 2 = */ "two");
  }
}