import org.jetbrains.annotations.NotNull;

class FieldSet<T> {
  public FieldSet<T> set(T @NotNull [] fields) {
    return this;
  }

  static void main() {
    final FieldSet<String> set = new FieldSet<>();
    set.set(new String[]{/* 1 = */ "one", /* 2 = */ "two"});
  }
}